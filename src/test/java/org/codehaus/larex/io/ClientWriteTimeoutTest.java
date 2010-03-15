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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.StandardClientConnector;
import org.codehaus.larex.io.connector.StandardEndpoint;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * @version $Revision$ $Date$
 */
public class ClientWriteTimeoutTest
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
    public void testWriteTimeout() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        StandardServerConnector serverConnector = new StandardServerConnector(address, new IdleConnection.Factory(), threadPool, scheduler);
        int port = serverConnector.listen();
        try
        {
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
                                public AtomicInteger writes = new AtomicInteger(0);

                                @Override
                                public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
                                {
                                    if (writes.addAndGet(1) == 1)
                                    {
                                        // Write only one byte
                                        ByteBuffer newBuffer = ByteBuffer.wrap(new byte[]{buffer.get()});
                                        return super.write(newBuffer);
                                    }
                                    else
                                    {
                                        return 0;
                                    }
                                }

                                @Override
                                public void onWriteReady()
                                {
                                    if (writes.get() > 1)
                                    {
                                        // Block write notifications, so that the test can timeout
                                        needsWrite(false);
                                    }
                                    else
                                    {
                                        super.onWriteReady();
                                    }
                                }
                            };
                        }
                    };
                }
            };
            Endpoint<IdleConnection> endpoint = connector.newEndpoint(new IdleConnection.Factory());
            endpoint.setWriteTimeout(1000);
            StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
            try
            {
                try
                {
                    connection.write(ByteBuffer.wrap(new byte[]{1, 2}));
                    fail();
                }
                catch (RuntimeSocketTimeoutException x)
                {
                    // Test passed
                }
            }
            finally
            {
                connection.softClose(1000);
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000);
        }
    }
}