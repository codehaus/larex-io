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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.connector.StandardClientConnector;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @version $Revision$ $Date$
 */
public class ServerWritesClosesClientIsNotifiedTest
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
    public void testRemoteClose() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);

        ConnectionFactory connectionFactory = new ConnectionFactory()
        {
            public Connection newConnection(Coordinator coordinator)
            {
                return new StandardConnection(coordinator)
                {
                    @Override
                    public void onOpen()
                    {
                        super.onOpen();
                        write(ByteBuffer.wrap(new byte[]{1}));
                        close();
                    }

                    @Override
                    protected void read(ByteBuffer buffer)
                    {
                    }
                };
            }
        };

        StandardServerConnector serverConnector = new StandardServerConnector(address, connectionFactory, threadPool, scheduler);
        int port = serverConnector.listen();

        final AtomicInteger tester = new AtomicInteger();
        final AtomicInteger failure = new AtomicInteger();
        final CountDownLatch latch = new CountDownLatch(1);
        StandardClientConnector connector = new StandardClientConnector(threadPool, scheduler);
        StandardConnection connection = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
        {
            public StandardConnection newConnection(Coordinator coordinator)
            {
                return new StandardConnection(coordinator)
                {
                    @Override
                    protected void read(ByteBuffer buffer)
                    {
                        if (!tester.compareAndSet(0, 1))
                            failure.set(1);
                    }

                    @Override
                    public void onRemoteClose()
                    {
                        if (!tester.compareAndSet(1, 2))
                            failure.set(2);
                        latch.countDown();
                    }
                };
            }
        }).connect(new InetSocketAddress(address.getHostName(), port));

        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
        assertEquals(2, tester.get());
        assertEquals(0, failure.get());

        connection.close();
    }
}
