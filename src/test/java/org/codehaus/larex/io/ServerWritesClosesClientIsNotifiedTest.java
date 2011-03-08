/*
 * Copyright (c) 2010 the original author or authors
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
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.connector.ClientConnector;
import org.codehaus.larex.io.connector.ServerConnector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServerWritesClosesClientIsNotifiedTest extends AbstractTestCase
{
    @Test
    public void testRemoteClose() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);

        ConnectionFactory connectionFactory = new ConnectionFactory()
        {
            public Connection newConnection(Controller controller)
            {
                return new StandardConnection(controller)
                {
                    @Override
                    public void onOpen()
                    {
                        write(ByteBuffer.wrap(new byte[]{1}));
                        close();
                    }
                };
            }
        };

        ServerConnector serverConnector = new ServerConnector(address, connectionFactory, getThreadPool());
        int port = serverConnector.listen();

        try
        {
            final AtomicInteger tester = new AtomicInteger();
            final AtomicInteger failure = new AtomicInteger();
            final CountDownLatch latch = new CountDownLatch(1);
            ClientConnector connector = new ClientConnector(getThreadPool());
            connector.open();

            try
            {
                StandardConnection connection = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
                {
                    public StandardConnection newConnection(Controller controller)
                    {
                        return new StandardConnection(controller)
                        {
                            @Override
                            protected boolean onRead(ByteBuffer buffer)
                            {
                                if (!tester.compareAndSet(0, 1))
                                    failure.set(1);
                                return super.onRead(buffer);
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

                try
                {
                    assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
                    assertEquals(2, tester.get());
                    assertEquals(0, failure.get());
                }
                finally
                {
                    connection.close();
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
