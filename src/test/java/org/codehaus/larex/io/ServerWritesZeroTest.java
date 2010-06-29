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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ServerWritesZeroTest extends AbstractTestCase
{
    @Test
    public void testWriteZero() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger needWrites = new AtomicInteger();
        InetSocketAddress address = new InetSocketAddress("localhost", 0);

        ServerConnector serverConnector = new ServerConnector(address, new EchoConnection.Factory(), getThreadPool())
        {
            @Override
            protected Channel newChannel(Selector selector, SocketChannel channel, Controller controller)
            {
                return new StandardChannel(selector, channel, controller)
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
            protected Coordinator newCoordinator(Selector selector)
            {
                return new StandardCoordinator(selector, getByteBuffers(), getThreadPool())
                {
                    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
                    {
                        writes.incrementAndGet();
                        return super.write(buffer);
                    }

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
            ClientConnector connector = new ClientConnector(getThreadPool());
            connector.open();
            try
            {
                Endpoint<StandardConnection> endpoint = connector.newEndpoint(new StandardConnection.Factory());
                StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
                assertTrue(connection.awaitReady(1000));
                try
                {
                    ByteBuffer buffer = ByteBuffer.wrap("HELLO".getBytes("UTF-8"));
                    connection.flush(buffer);

                    assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));

                    // Three write calls from the connection
                    assertEquals(3, writes.get());
                    // Four needsWrite calls:
                    // after writing 0 bytes to enable the writes, then to disable;
                    // after writing 1 byte to enable the writes, then to disable
                    assertEquals(4, needWrites.get());
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
