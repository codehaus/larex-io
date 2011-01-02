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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.codehaus.larex.io.connector.ServerConnector;

/**
 * @version $Revision: 13 $ $Date$
 */
public class LoadServerMain
{
    private static final AtomicLong selects = new AtomicLong();
    private static final AtomicLong wakeups = new AtomicLong();
    private static final AtomicLong updates = new AtomicLong();
    private static final AtomicLong reads = new AtomicLong();
    private static final AtomicLong writes = new AtomicLong();

    public static void main(String[] args) throws Exception
    {
        int maxThreads = 500;
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(maxThreads, maxThreads, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new CallerBlocksPolicy());

        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(null), 8850);

        ServerConnector connector = new ServerConnector(address, new LoadConnection.Factory(), threadPool)
        {
            @Override
            protected Selector newSelector()
            {
                TimeoutReadWriteSelector selector = new TimeoutReadWriteSelector()
                {
                    @Override
                    protected int select() throws IOException
                    {
                        selects.incrementAndGet();
                        return super.select();
                    }

                    @Override
                    protected void wakeup()
                    {
                        wakeups.incrementAndGet();
                        super.wakeup();
                    }
                };
                selector.open();
                return selector;
            }

            @Override
            protected Channel newChannel(Selector selector, SocketChannel channel, Controller controller)
            {
                return new StandardChannel(selector, channel, controller)
                {
                    @Override
                    public void run()
                    {
                        updates.incrementAndGet();
                        super.run();
                    }
                };
            }
        };
        connector.setAcceptorCount(1);
        connector.setSelectorCount(1);
        connector.listen();
    }

    private static void start()
    {
        selects.set(0);
        wakeups.set(0);
        updates.set(0);
        reads.set(0);
        writes.set(0);
    }

    private static void stop()
    {
        System.err.println("Selects: " + selects.get());
        System.err.println("Wakeups: " + wakeups.get());
        System.err.println("Updates: " + updates.get());
        System.err.println("Reads: " + reads.get());
        System.err.println("Writes: " + writes.get());
    }

    private static class LoadConnection extends EchoConnection
    {
        private byte type;
        private int typeBytes;
        private int timeBytes;
        private int length;
        private int lengthBytes;
        private int contentLength;

        private LoadConnection(Controller controller)
        {
            super(controller);
        }

        @Override
        protected void onOpen()
        {
            getController().setReadBufferSize(16384);
        }

        @Override
        protected boolean onRead(ByteBuffer buffer)
        {
            while (buffer.hasRemaining())
            {
                byte currByte = buffer.get();

                if (typeBytes < 1)
                {
                    type = currByte;
                    ++typeBytes;
                    continue;
                }
                else if (timeBytes < 8)
                {
                    ++timeBytes;
                    continue;
                }
                else if (lengthBytes < 2)
                {
                    length <<= 8;
                    length += currByte & 0xFF;
                    ++lengthBytes;
                    if (lengthBytes < 2)
                        continue;
                }

                int size = Math.min(length - contentLength, buffer.remaining());
                contentLength += size;
                buffer.position(buffer.position() + size);

                if (contentLength == length)
                {
                    if ((type & 0xFF) == 0xFE)
                        start();
                    else if ((type & 0xFF) == 0xFF)
                        stop();

                    type = 0;
                    typeBytes = 0;
                    timeBytes = 0;
                    length = 0;
                    lengthBytes = 0;
                    contentLength = 0;
                }
            }
            buffer.flip();
            return super.onRead(buffer);
        }

        private static class StatisticsInterceptor extends Interceptor.Forwarder
        {
            @Override
            public boolean onRead(ByteBuffer buffer)
            {
                reads.incrementAndGet();
                return super.onRead(buffer);
            }

            @Override
            public int write(ByteBuffer buffer)
            {
                writes.incrementAndGet();
                return super.write(buffer);
            }
        }

        private static class Factory implements ConnectionFactory<LoadConnection>
        {
            @Override
            public LoadConnection newConnection(Controller controller)
            {
                controller.addInterceptor(new StatisticsInterceptor());
                return new LoadConnection(controller);
            }
        }
    }
}
