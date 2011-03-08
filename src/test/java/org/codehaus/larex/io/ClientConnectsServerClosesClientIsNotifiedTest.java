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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.larex.io.connector.ClientConnector;
import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.ServerConnector;
import org.codehaus.larex.io.connector.StandardEndpoint;
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
            public StandardConnection newConnection(Controller controller)
            {
                return new StandardConnection(controller)
                {
                    @Override
                    public void onOpen()
                    {
                        serverConnection.set(this);
                        serverConnectionLatch.countDown();
                    }
                };
            }
        };
        ServerConnector serverConnector = new ServerConnector(address, connectionFactory, getThreadPool());
        int port = serverConnector.listen();
        try
        {
            ClientConnector connector = new ClientConnector(getThreadPool())
            {
                @Override
                public <T extends Connection> Endpoint<T> newEndpoint(ConnectionFactory<T> connectionFactory)
                {
                    return new StandardEndpoint<T>(connectionFactory, chooseReactor(), getByteBuffers(), getThreadPool())
                    {
                        @Override
                        protected void register(Channel channel, Reactor.Listener listener)
                        {
                            try
                            {
                                serverConnectionLatch.await(1, TimeUnit.SECONDS);
                                StandardConnection connection = serverConnection.get();
                                connection.write(ByteBuffer.wrap(new byte[]{1}));
                                connection.close();
                                Thread.sleep(500);
                                super.register(channel, listener);
                            }
                            catch (InterruptedException x)
                            {
                                throw new RuntimeIOException(x);
                            }
                        }
                    };
                }
            };
            connector.open();
            try
            {
                final CountDownLatch latch = new CountDownLatch(2);
                connector.newEndpoint(new ConnectionFactory<StandardConnection>()
                {
                    public StandardConnection newConnection(Controller controller)
                    {
                        return new StandardConnection(controller)
                        {
                            @Override
                            protected boolean onRead(ByteBuffer buffer)
                            {
                                latch.countDown();
                                return super.onRead(buffer);
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
