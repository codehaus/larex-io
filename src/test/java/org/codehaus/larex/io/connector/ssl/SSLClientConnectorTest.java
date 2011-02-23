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
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.codehaus.larex.io.AbstractTestCase;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.StandardConnection;
import org.codehaus.larex.io.StreamType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

public class SSLClientConnectorTest extends AbstractTestCase
{
    private SSLClientConnector clientConnector;

    @Before
    public void initClientConnector() throws Exception
    {
        clientConnector = new SSLClientConnector(getThreadPool());
        clientConnector.setKeyStoreResource("keystore");
        clientConnector.setKeyStorePassword("storepwd");
        clientConnector.setKeyPassword("keypwd");
        clientConnector.setTrustStoreResource("truststore");
        clientConnector.open();
    }

    @After
    public void destroyClientConnector() throws Exception
    {
        clientConnector.close();
        clientConnector.join(1000);
    }

    @Test
    public void testHandshake() throws Exception
    {
        SSLContext sslContext = clientConnector.getSSLContext();
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
            SSLEndpoint<StandardConnection> sslEndpoint = clientConnector.newEndpoint(new StandardConnection.Factory());
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
        SSLContext sslContext = clientConnector.getSSLContext();
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
            SSLEndpoint<StandardConnection> sslEndpoint = clientConnector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Controller controller)
                {
                    return new StandardConnection(controller)
                    {
                        @Override
                        public void onRemoteClose()
                        {
                            remoteCloseLatch.countDown();
                        }

                        @Override
                        public void onClosing(StreamType type)
                        {
                            closingLatch.countDown();
                        }

                        @Override
                        protected void onClosed(StreamType type)
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
        SSLContext sslContext = clientConnector.getSSLContext();
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
            SSLEndpoint<StandardConnection> sslEndpoint = clientConnector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Controller controller)
                {
                    return new StandardConnection(controller)
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
                        public void onClosing(StreamType type)
                        {
                            closingLatch.countDown();
                        }

                        @Override
                        protected void onClosed(StreamType type)
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
        SSLContext sslContext = clientConnector.getSSLContext();
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
            SSLEndpoint<StandardConnection> sslEndpoint = clientConnector.newEndpoint(new StandardConnection.Factory());
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

    @Test
    public void testHandshakeThenClientWritesBigBodyThenServerEchoesBack() throws Exception
    {
        SSLContext sslContext = clientConnector.getSSLContext();

        String chunk = "0123456789ABCDEF";
        final StringBuilder content = new StringBuilder();
        for (int i = 0; i < 64 * 1024 / chunk.length(); ++i)
            content.append(chunk);

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

                    InputStream input = sslSocket.getInputStream();
                    sslSocket.setSoTimeout(1000);
                    byte[] buffer = new byte[1024];
                    int total = 0;
                    while (true)
                    {
                        try
                        {
                            total += input.read(buffer);
                        }
                        catch (SocketTimeoutException x)
                        {
                            break;
                        }
                    }
                    assertEquals(content.length(), total);

                    OutputStream output = sslSocket.getOutputStream();
                    output.write(content.toString().getBytes("UTF-8"));
                    output.flush();

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
            final AtomicInteger bytesCount = new AtomicInteger();
            final CountDownLatch clientLatch = new CountDownLatch(1);
            SSLEndpoint<StandardConnection> sslEndpoint = clientConnector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Controller controller)
                {
                    return new StandardConnection(controller)
                    {
                        @Override
                        protected void onRead(ByteBuffer buffer)
                        {
                            bytesCount.addAndGet(buffer.remaining());
                            if (bytesCount.get() == content.length())
                                clientLatch.countDown();
                        }
                    };
                }
            });
            StandardConnection connection = sslEndpoint.connect(new InetSocketAddress("localhost", sslServerSocket.getLocalPort()));
            SSLEngine sslEngine = sslEndpoint.getSSLEngine();
            assertNotNull(sslEngine);

            assertTrue(connection.awaitReady(1000));

            connection.flush(ByteBuffer.wrap(content.toString().getBytes("UTF-8")));

            // It takes a while for the server to read and echo back
            assertTrue(serverLatch.await(2000, TimeUnit.MILLISECONDS));

            assertTrue(clientLatch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            acceptor.join();
            sslServerSocket.close();
        }
    }

    @Test
    public void testHandshakeWithExternalSite() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("mail.google.com", 443);

        // Test if we can connect to avoid failing the test if we cannot
        try
        {
            new Socket(address.getHostName(), address.getPort());
        }
        catch (UnknownHostException x)
        {
            assumeNoException(x);
        }
        catch (ConnectException x)
        {
            assumeNoException(x);
        }

        final CountDownLatch latch = new CountDownLatch(1);
        // Use a SSL connector not configured with the test keystore
        SSLClientConnector connector = new SSLClientConnector(getThreadPool());
        connector.open();
        try
        {
            StandardConnection connection = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Controller controller)
                {
                    return new StandardConnection(controller)
                    {
                        @Override
                        protected void onRead(ByteBuffer buffer)
                        {
                            String response = Charset.forName("UTF-8").decode(buffer).toString();
                            if (Pattern.compile("<html>", Pattern.CASE_INSENSITIVE).matcher(response).find())
                                latch.countDown();
                        }
                    };
                }
            }).connect(address);

            assertTrue(connection.awaitReady(1000));

            String request = "" +
                    "GET / HTTP/1.1\r\n" +
                    "Host: mail.google.com:443\r\n" +
                    "\r\n";

            connection.flush(Charset.forName("UTF-8").encode(request));
            assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            connector.close();
            connector.join(1000);
        }
    }

    @Test
    public void testHandshakeThenClientWritesThenServerEchoesThenClientClosesOutput() throws Exception
    {
        SSLContext sslContext = clientConnector.getSSLContext();

        final String clientMessage = "clientMessage";
        final String serverMessage = "serverMessage";
        final CountDownLatch serverRemoteCloseLatch = new CountDownLatch(1);
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

                    OutputStream output = sslSocket.getOutputStream();
                    output.write(clientMessage.getBytes("UTF-8"));
                    output.flush();

                    // Client has closed
                    read = input.read();
                    assertEquals(-1, read);

                    try
                    {
                        // SSL engine does not support writing after a remote close
                        output.write(serverMessage.getBytes("UTF-8"));
                        fail();
                    }
                    catch (SocketException x)
                    {
                        sslSocket.close();
                        serverRemoteCloseLatch.countDown();
                    }
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
            final AtomicReference<ByteBuffer> message = new AtomicReference<ByteBuffer>();
            final CountDownLatch readLatch = new CountDownLatch(1);
            final CountDownLatch clientRemoteCloseLatch = new CountDownLatch(1);
            SSLEndpoint<StandardConnection> sslEndpoint = clientConnector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                @Override
                public StandardConnection newConnection(Controller controller)
                {
                    return new StandardConnection(controller)
                    {
                        @Override
                        protected void onRead(ByteBuffer buffer)
                        {
                            if (readLatch.getCount() == 1)
                            {
                                message.set(ByteBuffer.allocate(buffer.remaining()).put(buffer));
                                readLatch.countDown();
                            }
                        }

                        @Override
                        protected void onRemoteClose()
                        {
                            clientRemoteCloseLatch.countDown();
                        }
                    };
                }
            });
            StandardConnection connection = sslEndpoint.connect(new InetSocketAddress("localhost", sslServerSocket.getLocalPort()));
            assertTrue(connection.awaitReady(1000));

            connection.flush(Charset.forName("UTF-8").encode(clientMessage));

            assertTrue(readLatch.await(1000, TimeUnit.MILLISECONDS));
            assertNotNull(message.get());
            assertEquals(clientMessage, Charset.forName("UTF-8").decode((ByteBuffer)message.get().flip()).toString());

            // Close output; the server will try to write one more message, but will fail
            connection.close(StreamType.OUTPUT);
            assertTrue(serverRemoteCloseLatch.await(1000, TimeUnit.MILLISECONDS));

            // The server has closed, we should read -1
            assertTrue(clientRemoteCloseLatch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            clientConnector.close();
        }
    }
}
