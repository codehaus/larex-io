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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.connector.ClientConnector;
import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.ServerConnector;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ServerReadsZeroTest extends AbstractTestCase
{
    @Test
    public void testReadZero() throws Exception
    {
        final CountDownLatch needReads = new CountDownLatch(5);
        final CountDownLatch reads = new CountDownLatch(1);
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        ServerConnector serverConnector = new ServerConnector(address, new StandardConnection.Factory(), getThreadPool())
        {
            @Override
            protected Channel newChannel(Reactor reactor, SocketChannel channel, Controller controller)
            {
                return new StandardChannel(reactor, channel, controller)
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
            protected Coordinator newCoordinator(Reactor reactor)
            {
                return new DispatchCoordinator(reactor, getByteBuffers(), getThreadPool(), getReadTimeout(), getWriteTimeout())
                {
                    @Override
                    protected boolean onRead(ByteBuffer buffer)
                    {
                        boolean result = super.onRead(buffer);
                        reads.countDown();
                        return result;
                    }

                    public void needsRead(boolean needsRead)
                    {
                        needReads.countDown();
                        super.needsRead(needsRead);
                    }
                };
            }
        };
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
                try
                {
                    ByteBuffer buffer = ByteBuffer.wrap("HELLO".getBytes("UTF-8"));
                    connection.flush(buffer);
                    assertTrue(reads.await(1000, TimeUnit.MILLISECONDS));

                    // Five needsRead calls: at beginning to enable the reads, then to disable;
                    // after reading 0 to re-enable the reads, and again to disable;
                    // then to re-enable them
                    assertTrue(needReads.await(1000, TimeUnit.MILLISECONDS));
                }
                finally
                {
                    connection.softClose(1000);
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
}
