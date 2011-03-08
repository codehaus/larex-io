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

package org.codehaus.larex.io;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.codehaus.larex.io.connector.ClientConnector;
import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.ServerConnector;
import org.codehaus.larex.io.connector.StandardEndpoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientClosesTest extends AbstractTestCase
{
    @Test
    public void testClientClosesServerIsNotified() throws Exception
    {
        final CountDownLatch closeLatch = new CountDownLatch(1);
        ServerConnector serverConnector = new ServerConnector(new InetSocketAddress("localhost", 0), new ConnectionFactory()
        {
            public Connection newConnection(Controller controller)
            {
                return new StandardConnection(controller)
                {
                    @Override
                    public void onRemoteClose()
                    {
                        closeLatch.countDown();
                    }
                };
            }
        }, getThreadPool());
        int port = serverConnector.listen();

        try
        {
            ClientConnector connector = new ClientConnector(getThreadPool());
            connector.open();
            try
            {
                Endpoint<StandardConnection> endpoint = connector.newEndpoint(new StandardConnection.Factory());
                StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
                assertTrue(connection.awaitOpened(1000));

                // In JDK 5, closing the connection does not cause a FIN to be sent to the server,
                // see http://bugs.sun.com/view_bug.do?bug_id=4960962.
                // The only option is to call softClose() or close(StreamType.OUTPUT)
//                connection.close();
                connection.softClose(1000);
                assertTrue(await(closeLatch, 1000));
            }
            finally
            {
                connector.close();
                connector.join(1000);
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000);
        }
    }

    /*
     * C                S
     *   --- SYN     -->
     *   <-- SYN/ACK ---
     *   --- ACK     -->
     *   --- PSH     -->
     *   <-- ACK     ---
     *   <-- PSH     ---
     *   --- ACK     -->
     *   --- RST     -->
     */
    @Test
    public void testClientClosesWithPendingRead() throws Exception
    {
        final CountDownLatch latch4 = new CountDownLatch(1);
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        ServerConnector serverConnector = new ServerConnector(address, new ConnectionFactory()
        {
            public Connection newConnection(Controller controller)
            {
                return new StandardConnection(controller)
                {
                    @Override
                    protected boolean onRead(ByteBuffer buffer)
                    {
                        write(ByteBuffer.wrap(new byte[]{1}));
                        return super.onRead(buffer);
                    }
                };
            }
        }, getThreadPool())
        {
            @Override
            protected Coordinator newCoordinator(Reactor reactor)
            {
                return new DispatchCoordinator(reactor, getByteBuffers(), getThreadPool(), getReadTimeout(), getWriteTimeout())
                {
                    @Override
                    protected void processOnRead()
                    {
                        // When a TCP packet is received by the client closed socket,
                        // the client closed socket sends a RST, which wakes up the
                        // server selector for read, but trying to read on server
                        // gives an IOException("Connection reset by peer");
                        try
                        {
                            super.processOnRead();
                        }
                        catch (RuntimeIOException x)
                        {
                            ClientClosesTest.this.logger.debug("Step 4 releasing");
                            latch4.countDown();
                        }
                    }
                };
            }
        };
        int port = serverConnector.listen();
        try
        {
            final CountDownLatch latch1 = new CountDownLatch(1);
            final CountDownLatch latch2 = new CountDownLatch(1);
            final CountDownLatch latch3 = new CountDownLatch(1);
            final AtomicBoolean read = new AtomicBoolean(false);
            ClientConnector connector = new ClientConnector(getThreadPool())
            {
                @Override
                public <T extends Connection> Endpoint<T> newEndpoint(ConnectionFactory<T> connectionFactory)
                {
                    return new StandardEndpoint<T>(connectionFactory, chooseReactor(), getByteBuffers(), getThreadPool())
                    {
                        @Override
                        protected Coordinator newCoordinator()
                        {
                            return new DispatchCoordinator(getReactor(), getByteBuffers(), getThreadPool(), getReadTimeout(), getWriteTimeout())
                            {
                                @Override
                                protected void processOnRead()
                                {
                                    ClientClosesTest.this.logger.debug("Step 1 releasing");
                                    latch1.countDown();

                                    if (await(latch2, 1000))
                                    {
                                        // Client tries to read, but the socket is closed already
                                        ClientClosesTest.this.logger.debug("Step 2 released");
                                        try
                                        {
                                            super.processOnRead();
                                        }
                                        catch (RuntimeSocketClosedException x)
                                        {
                                            ClientClosesTest.this.logger.debug("Step 3 releasing");
                                            latch3.countDown();
                                        }
                                    }
                                }
                            };
                        }
                    };
                }
            };
            connector.open();
            try
            {
                Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
                {
                    public StandardConnection newConnection(Controller controller)
                    {
                        return new StandardConnection(controller)
                        {
                            @Override
                            protected boolean onRead(ByteBuffer buffer)
                            {
                                read.set(true);
                                return super.onRead(buffer);
                            }
                        };
                    }
                });
                StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
                assertTrue(connection.awaitOpened(1000));
                try
                {
                    // Trigger server-side read, which will write to the client
                    connection.write(ByteBuffer.wrap(new byte[]{1}));

                    // Wait until the client is ready to read from server
                    if (await(latch1, 1000))
                    {
                        logger.debug("Step 1 released");
                        connection.close();
                        logger.debug("Step 2 releasing");
                        latch2.countDown();
                    }

                    assertTrue(await(latch3, 1000));
                    assertTrue(await(latch4, 1000));
                    assertFalse(read.get());
                }
                finally
                {
                    connection.close();
                }
            }
            finally
            {
                connector.close();
                connector.join(1000);
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000);
        }
    }

    @Test
    public void testClientClosesOutputWithPendingRead() throws Exception
    {
        final CountDownLatch latch4 = new CountDownLatch(1);
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        ServerConnector serverConnector = new ServerConnector(address, new ConnectionFactory()
        {
            public Connection newConnection(Controller controller)
            {
                return new StandardConnection(controller)
                {
                    @Override
                    protected boolean onRead(ByteBuffer buffer)
                    {
                        write(ByteBuffer.wrap(new byte[]{1}));
                        return super.onRead(buffer);
                    }

                    @Override
                    public void onRemoteClose()
                    {
                        ClientClosesTest.this.logger.debug("Step 4 releasing");
                        latch4.countDown();
                    }
                };
            }
        }, getThreadPool());
        int port = serverConnector.listen();
        try
        {
            final CountDownLatch latch1 = new CountDownLatch(1);
            final CountDownLatch latch2 = new CountDownLatch(1);
            final CountDownLatch latch3 = new CountDownLatch(1);
            final CountDownLatch latch5 = new CountDownLatch(1);
            ClientConnector connector = new ClientConnector(getThreadPool())
            {
                @Override
                public <T extends Connection> Endpoint<T> newEndpoint(ConnectionFactory<T> connectionFactory)
                {
                    return new StandardEndpoint<T>(connectionFactory, chooseReactor(), getByteBuffers(), getThreadPool())
                    {
                        @Override
                        protected Coordinator newCoordinator()
                        {
                            return new DispatchCoordinator(getReactor(), getByteBuffers(), getThreadPool(), getReadTimeout(), getWriteTimeout())
                            {
                                @Override
                                protected void processOnRead()
                                {
                                    ClientClosesTest.this.logger.debug("Step 1 releasing");
                                    latch1.countDown();

                                    if (await(latch2, 1000))
                                    {
                                        // Client reads normally since input has not been closed
                                        ClientClosesTest.this.logger.debug("Step 2 released");
                                        super.processOnRead();
                                    }
                                }
                            };
                        }
                    };
                }
            };
            connector.open();
            try
            {
                Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
                {
                    public StandardConnection newConnection(Controller controller)
                    {
                        return new StandardConnection(controller)
                        {
                            @Override
                            protected boolean onRead(ByteBuffer buffer)
                            {
                                ClientClosesTest.this.logger.debug("Step 3 releasing");
                                latch3.countDown();
                                return super.onRead(buffer);
                            }

                            @Override
                            public void onRemoteClose()
                            {
                                ClientClosesTest.this.logger.debug("Step 5 releasing");
                                latch5.countDown();
                            }
                        };
                    }
                });
                StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
                assertTrue(connection.awaitOpened(1000));
                try
                {
                    // Trigger server-side read, which will write to the client
                    connection.write(ByteBuffer.wrap(new byte[]{1}));

                    // Wait until the client is ready to read from server
                    if (await(latch1, 1000))
                    {
                        logger.debug("Step 1 released");
                        // Notify the server that we're closing
                        connection.close(StreamType.OUTPUT);
                        logger.debug("Step 2 releasing");
                        latch2.countDown();
                    }

                    assertTrue(await(latch3, 1000));
                    assertTrue(await(latch4, 1000));
                    assertTrue(await(latch5, 1000));
                }
                finally
                {
                    connection.close();
                }
            }
            finally
            {
                connector.close();
                connector.join(1000);
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000);
        }
    }

    private boolean await(CountDownLatch latch, long time)
    {
        try
        {
            return latch.await(time, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException x)
        {
            throw new RuntimeSocketTimeoutException(x);
        }
    }
}
