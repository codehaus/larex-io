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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

/**
 * @version $Revision: 903 $ $Date$
 */
public class AsyncServerConnectorReadZeroTest
{
    @Test
    public void testReadZero() throws Exception
    {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        try
        {
            final AtomicReference<ByteBuffer> data = new AtomicReference<ByteBuffer>();
            final CountDownLatch latch = new CountDownLatch(1);
            AsyncConnectorListener listener = new AsyncConnectorListener()
            {
                public AsyncInterpreter connected(AsyncCoordinator coordinator)
                {
                    return new EchoAsyncInterpreter(coordinator)
                    {
                        public void readFrom(ByteBuffer bytes)
                        {
                            data.set(bytes);
                            latch.countDown();
                        }
                    };
                }
            };

            final AtomicInteger reads = new AtomicInteger();
            final AtomicInteger needReads = new AtomicInteger();
            InetAddress loopback = InetAddress.getByName(null);
            InetSocketAddress address = new InetSocketAddress(loopback, 0);
            StandardAsyncServerConnector serverConnector = new StandardAsyncServerConnector(address, listener, threadPool)
            {
                @Override
                protected AsyncEndpoint newEndpoint(SocketChannel channel, AsyncCoordinator coordinator)
                {
                    return new StandardAsyncEndpoint(channel, coordinator)
                    {
                        private final AtomicInteger reads = new AtomicInteger();

                        @Override
                        protected int readAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
                        {
                            if (this.reads.compareAndSet(0, 1))
                            {
                                // In the first greedy read, we simulate a zero bytes read
                                return 0;
                            }
                            else
                            {
                                return super.readAggressively(channel, buffer);
                            }
                        }
                    };
                }

                @Override
                protected AsyncCoordinator newCoordinator()
                {
                    return new StandardAsyncCoordinator(getSelector(), getThreadPool())
                    {
                        @Override
                        public void readFrom(ByteBuffer bytes)
                        {
                            reads.incrementAndGet();
                            super.readFrom(bytes);
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
                    // Three needsRead calls: at beginning to disable the reads,
                    // after reading 0 to re-enable the reads, and again to disable
                    // the reads when the bytes are read.
                    Assert.assertEquals(3, needReads.get());
                }
                finally
                {
                    socket.close();
                }
            }
            finally
            {
                serverConnector.close();
            }
        }
        finally
        {
            threadPool.shutdown();
        }
    }
}
