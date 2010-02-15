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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @version $Revision: 903 $ $Date$
 */
public class AsyncServerConnectorWriteZeroTest
{
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
    public void testWriteZero() throws Exception
    {
        ConnectionFactory connectionFactory = new ConnectionFactory()
        {
            public Connection newConnection(final Coordinator coordinator)
            {
                return new EchoConnection(coordinator);
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger needWrites = new AtomicInteger();
        InetAddress loopback = InetAddress.getByName(null);
        InetSocketAddress address = new InetSocketAddress(loopback, 0);

        StandardServerConnector serverConnector = new StandardServerConnector(address, connectionFactory, threadPool, scheduler)
        {
            @Override
            protected Channel newAsyncChannel(SocketChannel channel, Coordinator coordinator, ByteBuffers byteBuffers)
            {
                return new StandardChannel(channel, coordinator, byteBuffers)
                {
                    private final AtomicInteger writes = new AtomicInteger();

                    @Override
                    protected int writeAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
                    {
                        if (this.writes.compareAndSet(0, 1))
                        {
                            // In the first aggressive write, we simulate a zero bytes write
                            return 0;
                        }
                        else if (this.writes.compareAndSet(1, 2))
                        {
                            // In the second aggressive write, simulate 1 byte write
                            ByteBuffer newBuffer = ByteBuffer.allocate(1);
                            newBuffer.put(buffer.get());
                            channel.write(newBuffer);
                            return newBuffer.capacity();
                        }
                        else
                        {
                            int result = super.writeAggressively(channel, buffer);
                            latch.countDown();
                            return result;
                        }
                    }
                };
            }

            @Override
            protected Coordinator newCoordinator(Selector selector, Executor threadPool)
            {
                return new StandardCoordinator(selector, threadPool)
                {
                    @Override
                    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
                    {
                        writes.incrementAndGet();
                        return super.write(buffer);
                    }

                    @Override
                    public void needsWrite(boolean needsWrite)
                    {
                        needWrites.incrementAndGet();
                        super.needsWrite(needsWrite);
                    }
                };
            }
        };
        int port = serverConnector.listen();
        try
        {
            Socket socket = new Socket(loopback, port);
            try
            {
                OutputStream output = socket.getOutputStream();
                byte[] bytes = "HELLO".getBytes("UTF-8");
                output.write(bytes);
                output.flush();

                Assert.assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));

                // Three write calls from the connection
                Assert.assertEquals(3, writes.get());
                // Four needsWrite calls:
                // after writing 0 bytes to enable the writes, then to disable;
                // after writing 1 byte to enable the writes, then to disable
                Assert.assertEquals(4, needWrites.get());
            }
            finally
            {
                socket.close();
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000L);
        }
    }
}
