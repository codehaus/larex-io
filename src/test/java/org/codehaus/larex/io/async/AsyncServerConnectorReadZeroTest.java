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

package org.codehaus.larex.io.async;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @version $Revision: 903 $ $Date$
 */
public class AsyncServerConnectorReadZeroTest
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
    public void testReadZero() throws Exception
    {
        final AtomicReference<ByteBuffer> data = new AtomicReference<ByteBuffer>();
        final CountDownLatch latch = new CountDownLatch(1);
        ConnectionFactory connectionFactory = new ConnectionFactory()
        {
            public Connection newConnection(Coordinator coordinator)
            {
                return new EchoConnection(coordinator)
                {
                    @Override
                    protected void read(ByteBuffer buffer)
                    {
                        data.set(copy(buffer));
                        latch.countDown();
                    }
                };
            }
        };

        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger needReads = new AtomicInteger();
        InetAddress loopback = InetAddress.getByName(null);
        InetSocketAddress address = new InetSocketAddress(loopback, 0);
        StandardServerConnector serverConnector = new StandardServerConnector(address, connectionFactory, threadPool, scheduler)
        {
            @Override
            protected Channel newAsyncChannel(SocketChannel channel, Coordinator coordinator, ByteBuffers byteBuffers)
            {
                return new StandardChannel(channel, coordinator, byteBuffers)
                {
                    private final AtomicInteger reads = new AtomicInteger();

                    @Override
                    protected boolean readAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
                    {
                        if (this.reads.compareAndSet(0, 1))
                        {
                            // In the first greedy read, we simulate a zero bytes read
                            return false;
                        }
                        else
                        {
                            return super.readAggressively(channel, buffer);
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
                    public void onRead(ByteBuffer bytes)
                    {
                        reads.incrementAndGet();
                        super.onRead(bytes);
                    }

                    @Override
                    public void needsRead(boolean needsRead)
                    {
                        needReads.incrementAndGet();
                        super.needsRead(needsRead);
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

                Assert.assertTrue(latch.await(10000, TimeUnit.MILLISECONDS));
                Assert.assertNotNull(data.get());
                ByteBuffer buffer = data.get();
                byte[] result = new byte[buffer.remaining()];
                buffer.get(result);
                Assert.assertTrue(Arrays.equals(bytes, result));
                // One read call, since with 0 bytes read we don't call it
                Assert.assertEquals(1, reads.get());
                // Five needsRead calls: at beginning to enable the reads, then to disable
                // after reading 0 to re-enable the reads, and again to disable
                // then to re-enable them
                Assert.assertEquals(5, needReads.get());
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
