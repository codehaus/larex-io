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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.StandardClientConnector;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardClientConnectorTest
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
    public void testConnect() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);

        final CountDownLatch acceptLatch = new CountDownLatch(1);
        StandardServerConnector serverConnector = new StandardServerConnector(address, new EchoConnection.Factory(), threadPool, scheduler)
        {
            @Override
            protected void accepted(SocketChannel channel) throws IOException
            {
                super.accepted(channel);
                acceptLatch.countDown();
            }
        };
        int port = serverConnector.listen();

        try
        {
            final CountDownLatch responseLatch = new CountDownLatch(1);
            StandardClientConnector clientConnector = new StandardClientConnector(threadPool, scheduler);
            Endpoint<StandardConnection> endpoint = clientConnector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new StandardConnection(coordinator)
                    {
                        @Override
                        protected void read(ByteBuffer buffer)
                        {
                            responseLatch.countDown();
                        }
                    };
                }
            });

            StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
            try
            {
                assertTrue(acceptLatch.await(1000, TimeUnit.MILLISECONDS));
                connection.write(ByteBuffer.wrap(new byte[]{1}));
                assertTrue(responseLatch.await(1000, TimeUnit.MILLISECONDS));
            }
            finally
            {
                connection.close();
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000L);
        }
    }
}
