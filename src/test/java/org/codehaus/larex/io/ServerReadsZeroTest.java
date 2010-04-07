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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.StandardClientConnector;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @version $Revision: 903 $ $Date$
 */
public class ServerReadsZeroTest extends AbstractTestCase
{
    @Test
    public void testReadZero() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        ConnectionFactory connectionFactory = new ConnectionFactory()
        {
            public Connection newConnection(Coordinator coordinator)
            {
                return new StandardConnection(coordinator)
                {
                    @Override
                    protected void read(ByteBuffer buffer)
                    {
                        latch.countDown();
                    }
                };
            }
        };

        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger needReads = new AtomicInteger();
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        StandardServerConnector serverConnector = new StandardServerConnector(address, connectionFactory, getThreadPool(), getScheduler())
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
            protected Coordinator newCoordinator(Selector selector, Executor threadPool, Scheduler scheduler)
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
            StandardClientConnector connector = new StandardClientConnector(getThreadPool(), getScheduler());
            Endpoint<IdleConnection> endpoint = connector.newEndpoint(new IdleConnection.Factory());
            StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
            try
            {
                ByteBuffer buffer = ByteBuffer.wrap("HELLO".getBytes("UTF-8"));
                connection.write(buffer);
                assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));

                // One read call, since with 0 bytes read we don't call it
                assertEquals(1, reads.get());

                // Five needsRead calls: at beginning to enable the reads, then to disable
                // after reading 0 to re-enable the reads, and again to disable
                // then to re-enable them
                assertEquals(5, needReads.get());
            }
            finally
            {
                connection.softClose(1000);
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000L);
        }
    }
}
