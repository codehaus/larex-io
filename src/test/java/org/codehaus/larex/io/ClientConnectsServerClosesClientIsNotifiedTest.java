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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.StandardClientConnector;
import org.codehaus.larex.io.connector.StandardEndpoint;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @version $Revision$ $Date$
 */
public class ClientConnectsServerClosesClientIsNotifiedTest extends AbstractTestCase
{
    @Test
    public void testClientConnectsServerClosesClientIsNotified() throws Exception
    {
        final AtomicReference<StandardConnection> serverConnection = new AtomicReference<StandardConnection>();
        final CountDownLatch serverConnectionLatch = new CountDownLatch(1);
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        ConnectionFactory<StandardConnection> connectionFactory = new ConnectionFactory<StandardConnection>()
        {
            public StandardConnection newConnection(Coordinator coordinator)
            {
                return new StandardConnection(coordinator)
                {
                    @Override
                    public void onReady()
                    {
                        serverConnection.set(this);
                        serverConnectionLatch.countDown();
                    }
                };
            }
        };
        StandardServerConnector serverConnector = new StandardServerConnector(address, connectionFactory, getThreadPool(), getScheduler());
        int port = serverConnector.listen();
        StandardClientConnector connector = new StandardClientConnector(getThreadPool(), getScheduler())
        {
            @Override
            protected <T extends Connection> Endpoint<T> newEndpoint(Selector selector, ConnectionFactory<T> connectionFactory, ByteBuffers byteBuffers, Executor threadPool, Scheduler scheduler)
            {
                return new StandardEndpoint<T>(selector, connectionFactory, byteBuffers, threadPool, scheduler)
                {
                    @Override
                    protected void register(Channel channel, Coordinator coordinator)
                    {
                        try
                        {
                            serverConnectionLatch.await(1, TimeUnit.SECONDS);
                            StandardConnection connection = serverConnection.get();
                            connection.flush(ByteBuffer.wrap(new byte[]{1}));
                            connection.close();
                            Thread.sleep(500);
                            super.register(channel, coordinator);
                        }
                        catch (InterruptedException x)
                        {
                            throw new RuntimeIOException(x);
                        }
                    }
                };
            }
        };
        final CountDownLatch latch = new CountDownLatch(2);
        connector.newEndpoint(new ConnectionFactory<StandardConnection>()
        {
            public StandardConnection newConnection(Coordinator coordinator)
            {
                return new StandardConnection(coordinator)
                {
                    @Override
                    protected void onRead(ByteBuffer buffer)
                    {
                        latch.countDown();
                    }

                    @Override
                    public void onRemoteClose()
                    {
                        latch.countDown();
                    }
                };
            }
        }).connect(new InetSocketAddress("localhost", port));
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }
}
