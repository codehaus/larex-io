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

package org.codehaus.larex.io;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.StandardClientConnector;
import org.codehaus.larex.io.connector.StandardEndpoint;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @version $Revision$ $Date$
 */
public class ClientClosesTest
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private ExecutorService threadPool;
    private ScheduledExecutorService scheduler;

    @Before
    public void init()
    {
        threadPool = Executors.newCachedThreadPool();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void destroy()
    {
        scheduler.shutdown();
        threadPool.shutdown();
    }

    @Test
    public void testClientClosesServerIsNotified() throws Exception
    {
        final CountDownLatch closeLatch = new CountDownLatch(1);
        StandardServerConnector serverConnector = new StandardServerConnector(new InetSocketAddress("localhost", 0), new ConnectionFactory()
        {
            public Connection newConnection(Coordinator coordinator)
            {
                return new StandardConnection(coordinator)
                {
                    @Override
                    protected void read(ByteBuffer buffer)
                    {
                    }

                    @Override
                    public void onRemoteClose()
                    {
                        closeLatch.countDown();
                    }
                };
            }
        }, threadPool, scheduler);
        int port = serverConnector.listen();

        try
        {
            final CountDownLatch openLatch = new CountDownLatch(1);
            StandardClientConnector connector = new StandardClientConnector(threadPool, scheduler);
            Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new StandardConnection(coordinator)
                    {
                        @Override
                        public void onReady()
                        {
                            openLatch.countDown();
                        }

                        @Override
                        protected void read(ByteBuffer buffer)
                        {
                        }
                    };
                }
            });
            StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
            await(openLatch, 1000);
            connection.close();
            await(closeLatch, 1000);
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
        StandardServerConnector serverConnector = new StandardServerConnector(address, new ConnectionFactory()
        {
            public Connection newConnection(Coordinator coordinator)
            {
                return new StandardConnection(coordinator)
                {
                    @Override
                    protected void read(ByteBuffer buffer)
                    {
                        write(ByteBuffer.wrap(new byte[]{1}));
                    }
                };
            }
        }, threadPool, scheduler)
        {
            @Override
            protected Coordinator newCoordinator(Selector selector, Executor threadPool, ScheduledExecutorService scheduler)
            {
                return new TimeoutCoordinator(selector, threadPool, scheduler, getReadTimeout(), getWriteTimeout())
                {
                    @Override
                    protected void read()
                    {
                        // When a TCP packet is received by the client closed socket,
                        // the client closed socket sends a RST, which wakes up the
                        // server selector for read, but trying to read on server
                        // gives an IOException("Connection reset by peer");
                        try
                        {
                            super.read();
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
            StandardClientConnector connector = new StandardClientConnector(threadPool, scheduler)
            {
                @Override
                protected <T extends Connection> Endpoint<T> newEndpoint(Selector selector, ConnectionFactory<T> connectionFactory, ByteBuffers byteBuffers, Executor threadPool, ScheduledExecutorService scheduler)
                {
                    return new StandardEndpoint<T>(selector, connectionFactory, byteBuffers, threadPool, scheduler)
                    {
                        @Override
                        protected Coordinator newCoordinator(Selector selector, Executor threadPool, ScheduledExecutorService scheduler)
                        {
                            return new TimeoutCoordinator(selector, threadPool, scheduler, getReadTimeout(), getWriteTimeout())
                            {
                                @Override
                                protected void read()
                                {
                                    ClientClosesTest.this.logger.debug("Step 1 releasing");
                                    latch1.countDown();

                                    if (await(latch2, 1000))
                                    {
                                        // Client tries to read, but the socket is closed already
                                        ClientClosesTest.this.logger.debug("Step 2 released");
                                        try
                                        {
                                            super.read();
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
            Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new StandardConnection(coordinator)
                    {
                        @Override
                        protected void read(ByteBuffer buffer)
                        {
                            read.set(true);
                        }
                    };
                }
            });
            StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
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
            serverConnector.close();
            serverConnector.join(1000);
        }
    }

    @Test
    public void testClientClosesOutputWithPendingRead() throws Exception
    {
        final CountDownLatch latch4 = new CountDownLatch(1);
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        StandardServerConnector serverConnector = new StandardServerConnector(address, new ConnectionFactory()
        {
            public Connection newConnection(Coordinator coordinator)
            {
                return new StandardConnection(coordinator)
                {
                    @Override
                    protected void read(ByteBuffer buffer)
                    {
                        write(ByteBuffer.wrap(new byte[]{1}));
                    }

                    @Override
                    public void onRemoteClose()
                    {
                        ClientClosesTest.this.logger.debug("Step 4 releasing");
                        latch4.countDown();
                    }
                };
            }
        }, threadPool, scheduler);
        int port = serverConnector.listen();
        try
        {
            final CountDownLatch latch1 = new CountDownLatch(1);
            final CountDownLatch latch2 = new CountDownLatch(1);
            final CountDownLatch latch3 = new CountDownLatch(1);
            final CountDownLatch latch5 = new CountDownLatch(1);
            StandardClientConnector connector = new StandardClientConnector(threadPool, scheduler)
            {
                @Override
                protected <T extends Connection> Endpoint<T> newEndpoint(Selector selector, ConnectionFactory<T> connectionFactory, ByteBuffers byteBuffers, Executor threadPool, ScheduledExecutorService scheduler)
                {
                    return new StandardEndpoint<T>(selector, connectionFactory, byteBuffers, threadPool, scheduler)
                    {
                        @Override
                        protected Coordinator newCoordinator(Selector selector, Executor threadPool, ScheduledExecutorService scheduler)
                        {
                            return new TimeoutCoordinator(selector, threadPool, scheduler, getReadTimeout(), getWriteTimeout())
                            {
                                @Override
                                protected void read()
                                {
                                    ClientClosesTest.this.logger.debug("Step 1 releasing");
                                    latch1.countDown();

                                    if (await(latch2, 1000))
                                    {
                                        // Client reads normally since input has not been closed
                                        ClientClosesTest.this.logger.debug("Step 2 released");
                                        super.read();
                                    }
                                }
                            };
                        }
                    };
                }
            };
            Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new StandardConnection(coordinator)
                    {
                        @Override
                        protected void read(ByteBuffer buffer)
                        {
                            ClientClosesTest.this.logger.debug("Step 3 releasing");
                            latch3.countDown();
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
            try
            {
                // Trigger server-side read, which will write to the client
                connection.write(ByteBuffer.wrap(new byte[]{1}));

                // Wait until the client is ready to read from server
                if (await(latch1, 1000))
                {
                    logger.debug("Step 1 released");
                    // Notify the server that we're closing
                    connection.close(CloseType.OUTPUT);
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
