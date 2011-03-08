/*
 * Copyright (c) 2010 the original author or authors
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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.codehaus.larex.io.AbstractTestCase;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.EchoConnection;
import org.codehaus.larex.io.StreamType;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Ignore
public class SSLServerConnectorTest extends AbstractTestCase
{
    private SSLServerConnector connector;

    public int initServerConnector(ConnectionFactory connectionFactory) throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        connector = new SSLServerConnector(address, connectionFactory, getThreadPool());
        connector.setKeyStoreResource("keystore");
        connector.setKeyStorePassword("storepwd");
        connector.setKeyPassword("keypwd");
        connector.setTrustStoreResource("truststore");
        return connector.listen();
    }

    @After
    public void destroyServerConnector() throws Exception
    {
        connector.close();
        connector.join(1000);
    }

    @Test
    public void testHandshake() throws Exception
    {
        int port = initServerConnector(new EchoConnection.Factory());

        SSLContext sslContext = connector.getSSLContext();
        SSLSocket sslSocket = (SSLSocket)sslContext.getSocketFactory().createSocket("localhost", port);
        sslSocket.setUseClientMode(true);
        try
        {
            final CountDownLatch latch = new CountDownLatch(1);
            sslSocket.addHandshakeCompletedListener(new HandshakeCompletedListener()
            {
                public void handshakeCompleted(HandshakeCompletedEvent handshakeCompletedEvent)
                {
                    latch.countDown();
                }
            });
            sslSocket.startHandshake();

            assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            sslSocket.close();
        }
    }

    @Test
    public void testHandshakeThenClientWritesThenServerEchoesThenClientCloses() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        int port = initServerConnector(new ConnectionFactory()
        {
            public Connection newConnection(Controller controller)
            {
                return new EchoConnection(controller)
                {
                    @Override
                    protected void onRemoteClose()
                    {
                        latch.countDown();
                    }
                };
            }
        });

        // Use a plain socket to connect, so that it can be closed later
        Socket socket = new Socket("localhost", port);
        // Wrap the socket
        SSLContext sslContext = connector.getSSLContext();
        SSLSocket sslSocket = (SSLSocket)sslContext.getSocketFactory().createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();

        // Send something, wait for echo, then close
        String clientMessage = "clientMessage";
        OutputStream output = sslSocket.getOutputStream();
        output.write(clientMessage.getBytes("UTF-8"));
        output.flush();

        InputStream input = sslSocket.getInputStream();
        byte[] buffer = new byte[clientMessage.length()];
        int read = input.read(buffer);
        assertEquals(clientMessage.length(), read);
        assertEquals(clientMessage, new String(buffer, "UTF-8"));

        sslSocket.close();

        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testHandshakeThenClientWritesThenServerEchoesThenClientAbruptlyCloses() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        int port = initServerConnector(new ConnectionFactory()
        {
            public Connection newConnection(Controller controller)
            {
                return new EchoConnection(controller)
                {
                    @Override
                    protected void onRemoteClose()
                    {
                        latch.countDown();
                    }
                };
            }
        });

        // Use a plain socket to connect, so that it can be closed later
        Socket socket = new Socket("localhost", port);
        // Wrap the socket
        SSLContext sslContext = connector.getSSLContext();
        SSLSocket sslSocket = (SSLSocket)sslContext.getSocketFactory().createSocket(socket, socket.getInetAddress().getHostAddress(), socket.getPort(), true);
        sslSocket.setUseClientMode(true);
        sslSocket.startHandshake();

        // Send something, wait for echo, then abruptly close
        String clientMessage = "clientMessage";
        OutputStream output = sslSocket.getOutputStream();
        output.write(clientMessage.getBytes("UTF-8"));
        output.flush();

        InputStream input = sslSocket.getInputStream();
        byte[] buffer = new byte[clientMessage.length()];
        int read = input.read(buffer);
        assertEquals(clientMessage.length(), read);
        assertEquals(clientMessage, new String(buffer, "UTF-8"));

        // Close the underlying socket, not the SSL socket, to simulate abrupt close
        socket.close();

        assertFalse(latch.await(1000, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testHandshakeThenClientWritesThenServerEchoesThenServerClosesInput() throws Exception
    {
        final AtomicInteger reads = new AtomicInteger();
        final CountDownLatch remoteCloseLatch = new CountDownLatch(1);
        int port = initServerConnector(new ConnectionFactory()
        {
            public Connection newConnection(Controller controller)
            {
                return new EchoConnection(controller)
                {
                    @Override
                    protected boolean onRead(ByteBuffer buffer)
                    {
                        reads.incrementAndGet();
                        boolean result = super.onRead(buffer);
                        close(StreamType.INPUT);
                        return result;
                    }

                    @Override
                    protected void onRemoteClose()
                    {
                        remoteCloseLatch.countDown();
                    }
                };
            }
        });

        SSLContext sslContext = connector.getSSLContext();
        SSLSocket sslSocket = (SSLSocket)sslContext.getSocketFactory().createSocket("localhost", port);
        sslSocket.setUseClientMode(true);
        try
        {
            final CountDownLatch handshakeLatch = new CountDownLatch(1);
            sslSocket.addHandshakeCompletedListener(new HandshakeCompletedListener()
            {
                public void handshakeCompleted(HandshakeCompletedEvent handshakeCompletedEvent)
                {
                    handshakeLatch.countDown();
                }
            });
            sslSocket.startHandshake();

            assertTrue(handshakeLatch.await(1000, TimeUnit.MILLISECONDS));

            String clientMessage = "clientMessage";
            OutputStream output = sslSocket.getOutputStream();
            output.write(clientMessage.getBytes("UTF-8"));
            output.flush();

            InputStream input = sslSocket.getInputStream();
            byte[] buffer = new byte[clientMessage.length()];
            int read = input.read(buffer);
            assertEquals(clientMessage.length(), read);
            assertEquals(clientMessage, new String(buffer, "UTF-8"));

            // Write again, but now the server should have closed input
            // So this write is silently discarded by the server
            output.write(clientMessage.getBytes("UTF-8"));
            output.flush();
            try
            {
                sslSocket.setSoTimeout(1000);
                read = input.read(buffer);
                fail(String.valueOf(read));
            }
            catch (SocketTimeoutException x)
            {
                assertTrue(true);
            }

            // Be sure the second read has not been notified to the server
            assertEquals(1, reads.get());

            // The server cannot see this close because it has closed its input
            // so it cannot read the -1 that signal that the other peer has closed
            sslSocket.close();
            assertFalse(remoteCloseLatch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            sslSocket.close();
        }
    }

    // TODO: add more tests
}
