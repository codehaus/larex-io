/*
 * Copyright (c) 2010-2010 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.larex.io.connector.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.codehaus.larex.io.AbstractTestCase;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Coordinator;
import org.codehaus.larex.io.StandardConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @version $Revision$ $Date$
 */
public class SSLClientConnectorTest extends AbstractTestCase
{
    private SSLClientConnector connector;

    @Before
    public void initConnector() throws Exception
    {
        connector = new SSLClientConnector(getThreadPool(), getScheduler());
        connector.setKeyStoreResource("keystore");
        connector.setKeyStorePassword("storepwd");
        connector.setKeyPassword("keypwd");
        connector.setTrustStoreResource("truststore");
    }

    @After
    public void destroyConnector() throws Exception
    {
        connector.close();
        connector.join(1000);
    }

    @Test
    public void testHandshake() throws Exception
    {
        SSLContext sslContext = connector.getSSLContext();
        final CountDownLatch handshakeLatch = new CountDownLatch(1);
        final SSLServerSocket sslServerSocket = (SSLServerSocket)sslContext.getServerSocketFactory().createServerSocket(0);
        Thread acceptor = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();
                    sslSocket.setUseClientMode(false);
                    sslSocket.startHandshake();
                    handshakeLatch.countDown();
                }
                catch (IOException x)
                {
                    x.printStackTrace();
                }
            }
        };
        acceptor.start();
        try
        {
            SSLEndpoint<StandardConnection> sslEndpoint = connector.newEndpoint(new StandardConnection.Factory());
            StandardConnection connection = sslEndpoint.connect(new InetSocketAddress("localhost", sslServerSocket.getLocalPort()));
            SSLEngine sslEngine = sslEndpoint.getSSLEngine();
            assertNotNull(sslEngine);

            assertTrue(handshakeLatch.await(1000, TimeUnit.MILLISECONDS));
            assertTrue(connection.awaitReady(1000));
            assertEquals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, sslEngine.getHandshakeStatus());
        }
        finally
        {
            acceptor.join();
            sslServerSocket.close();
        }
    }

    @Test
    public void testHandshakeThenServerCloses() throws Exception
    {
        SSLContext sslContext = connector.getSSLContext();
        final CountDownLatch serverLatch = new CountDownLatch(1);
        final SSLServerSocket sslServerSocket = (SSLServerSocket)sslContext.getServerSocketFactory().createServerSocket(0);
        Thread acceptor = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();
                    sslSocket.setUseClientMode(false);
                    sslSocket.startHandshake();
                    sslSocket.close();
                    serverLatch.countDown();
                }
                catch (IOException x)
                {
                    x.printStackTrace();
                }
            }
        };
        acceptor.start();
        try
        {
            final CountDownLatch remoteCloseLatch = new CountDownLatch(1);
            final CountDownLatch closingLatch = new CountDownLatch(1);
            final CountDownLatch closedLatch = new CountDownLatch(1);
            SSLEndpoint<StandardConnection> sslEndpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new StandardConnection(coordinator)
                    {
                        @Override
                        public void onRemoteClose()
                        {
                            remoteCloseLatch.countDown();
                        }

                        @Override
                        public void onClosing()
                        {
                            closingLatch.countDown();
                        }

                        @Override
                        protected void onClosed()
                        {
                            closedLatch.countDown();
                        }
                    };
                }
            });
            sslEndpoint.connect(new InetSocketAddress("localhost", sslServerSocket.getLocalPort()));
            SSLEngine sslEngine = sslEndpoint.getSSLEngine();
            assertNotNull(sslEngine);

            assertTrue(serverLatch.await(1000, TimeUnit.MILLISECONDS));
            assertTrue(remoteCloseLatch.await(1000, TimeUnit.MILLISECONDS));
            assertTrue(closingLatch.await(1000, TimeUnit.MILLISECONDS));
            assertTrue(closedLatch.await(1000, TimeUnit.MILLISECONDS));
            assertEquals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, sslEngine.getHandshakeStatus());
        }
        finally
        {
            acceptor.join();
            sslServerSocket.close();
        }
    }

    @Test
    public void testHandshakeThenServerWritesThenCloses() throws Exception
    {
        SSLContext sslContext = connector.getSSLContext();
        final String serverMessage = "FROM_SERVER";
        final CountDownLatch serverLatch = new CountDownLatch(1);
        final SSLServerSocket sslServerSocket = (SSLServerSocket)sslContext.getServerSocketFactory().createServerSocket(0);
        Thread acceptor = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();
                    sslSocket.setUseClientMode(false);
                    sslSocket.startHandshake();
                    OutputStream output = sslSocket.getOutputStream();
                    output.write(serverMessage.getBytes("UTF-8"));
                    sslSocket.close();
                    serverLatch.countDown();
                }
                catch (IOException x)
                {
                    x.printStackTrace();
                }
            }
        };
        acceptor.start();
        try
        {
            final CountDownLatch readLatch = new CountDownLatch(1);
            final CountDownLatch remoteCloseLatch = new CountDownLatch(1);
            final CountDownLatch closingLatch = new CountDownLatch(1);
            final CountDownLatch closedLatch = new CountDownLatch(1);
            SSLEndpoint<StandardConnection> sslEndpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new StandardConnection(coordinator)
                    {
                        @Override
                        public void onRead(ByteBuffer buffer)
                        {
                            assertEquals(serverMessage, Charset.forName("UTF-8").decode(buffer).toString());
                            readLatch.countDown();
                        }

                        @Override
                        public void onRemoteClose()
                        {
                            remoteCloseLatch.countDown();
                        }

                        @Override
                        public void onClosing()
                        {
                            closingLatch.countDown();
                        }

                        @Override
                        protected void onClosed()
                        {
                            closedLatch.countDown();
                        }
                    };
                }
            });
            sslEndpoint.connect(new InetSocketAddress("localhost", sslServerSocket.getLocalPort()));
            SSLEngine sslEngine = sslEndpoint.getSSLEngine();
            assertNotNull(sslEngine);

            assertTrue(serverLatch.await(1000, TimeUnit.MILLISECONDS));
            assertTrue(readLatch.await(1000, TimeUnit.MILLISECONDS));
            assertTrue(remoteCloseLatch.await(1000, TimeUnit.MILLISECONDS));
            assertTrue(closingLatch.await(1000, TimeUnit.MILLISECONDS));
            assertTrue(closedLatch.await(1000, TimeUnit.MILLISECONDS));
            assertEquals(SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING, sslEngine.getHandshakeStatus());
        }
        finally
        {
            acceptor.join();
            sslServerSocket.close();
        }
    }

    @Test
    public void testHandshakeThenClientWritesThenClientCloses() throws Exception
    {
        SSLContext sslContext = connector.getSSLContext();
        final String clientMessage = "FROM_CLIENT";
        final CountDownLatch serverLatch = new CountDownLatch(1);
        final CountDownLatch serverCloseLatch = new CountDownLatch(1);
        final SSLServerSocket sslServerSocket = (SSLServerSocket)sslContext.getServerSocketFactory().createServerSocket(0);
        Thread acceptor = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    SSLSocket sslSocket = (SSLSocket)sslServerSocket.accept();
                    sslSocket.setUseClientMode(false);
                    sslSocket.startHandshake();

                    InputStream input = sslSocket.getInputStream();
                    byte[] buffer = new byte[clientMessage.length()];
                    int read = input.read(buffer);
                    assertEquals(clientMessage.length(), read);
                    assertEquals(clientMessage, new String(buffer, "UTF-8"));

                    serverLatch.countDown();

                    read = input.read();
                    assertEquals(-1, read);
                    serverCloseLatch.countDown();
                }
                catch (IOException x)
                {
                    x.printStackTrace();
                }
            }
        };
        acceptor.start();
        try
        {
            SSLEndpoint<StandardConnection> sslEndpoint = connector.newEndpoint(new StandardConnection.Factory());
            StandardConnection connection = sslEndpoint.connect(new InetSocketAddress("localhost", sslServerSocket.getLocalPort()));
            SSLEngine sslEngine = sslEndpoint.getSSLEngine();
            assertNotNull(sslEngine);

            assertTrue(connection.awaitReady(1000));

            connection.flush(ByteBuffer.wrap(clientMessage.getBytes("UTF-8")));
            assertTrue(serverLatch.await(1000, TimeUnit.MILLISECONDS));

            connection.close();
            assertTrue(serverCloseLatch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            acceptor.join();
            sslServerSocket.close();
        }
    }

    // TODO test big body

    @Test
    public void testExternalSite() throws Exception
    {
        SSLClientConnector connector = new SSLClientConnector(getThreadPool(), getScheduler());
        StandardConnection connection = connector.newEndpoint(new StandardConnection.Factory())
                .connect(new InetSocketAddress("mail.google.com", 443));

        assertTrue(connection.awaitReady(1000));

        connector.close();
        connector.join(1000);
    }
}
