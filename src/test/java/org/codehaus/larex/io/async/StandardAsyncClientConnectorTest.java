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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.larex.io.ClientConnector;
import org.codehaus.larex.io.RuntimeSocketConnectException;
import org.codehaus.larex.io.ServerConnector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardAsyncClientConnectorTest
{
    private ExecutorService threadPool;

    @Before
    public void setUp() throws Exception
    {
        threadPool = Executors.newCachedThreadPool();
    }

    @After
    public void tearDown() throws Exception
    {
        threadPool.shutdown();
    }

    @Test
    public void testConnect() throws Exception
    {
        InetAddress loopback = InetAddress.getByName(null);
        InetSocketAddress address = new InetSocketAddress(loopback, 0);

        AsyncConnectorListener serverListener = new AsyncConnectorListener()
        {
            public AsyncInterpreter connected(AsyncCoordinator coordinator)
            {
                return new EchoAsyncInterpreter(coordinator);
            }
        };

        final CountDownLatch responseLatch = new CountDownLatch(1);
        final AtomicReference<AsyncCoordinator> clientCoordinatorRef = new AtomicReference<AsyncCoordinator>();
        AsyncConnectorListener clientListener = new AsyncConnectorListener()
        {
            public AsyncInterpreter connected(AsyncCoordinator coordinator)
            {
                clientCoordinatorRef.set(coordinator);
                return new EchoAsyncInterpreter(coordinator)
                {
                    @Override
                    public void readFrom(ByteBuffer buffer)
                    {
                        responseLatch.countDown();
                    }
                };
            }
        };

        final CountDownLatch acceptLatch = new CountDownLatch(1);
        ServerConnector serverConnector = new StandardAsyncServerConnector(address, serverListener, threadPool)
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
            ClientConnector clientConnector = new StandardAsyncClientConnector(clientListener, threadPool);
            try
            {
                clientConnector.connect(new InetSocketAddress(loopback, port));
                assertTrue(acceptLatch.await(1000, TimeUnit.MILLISECONDS));

                clientCoordinatorRef.get().writeFrom(ByteBuffer.wrap(new byte[]{1}));
                assertTrue(responseLatch.await(1000, TimeUnit.MILLISECONDS));
            }
            finally
            {
                clientConnector.close();
            }
        }
        finally
        {
            serverConnector.close();
        }
    }

    @Test
    public void testFailedConnectCloses() throws Exception
    {
        InetAddress loopback = InetAddress.getByName(null);
        InetSocketAddress address = new InetSocketAddress(loopback, 0);
        ServerConnector serverConnector = new StandardAsyncServerConnector(address, null, threadPool);
        int port = serverConnector.listen();
        address = new InetSocketAddress(address.getAddress(), port);
        serverConnector.close();
        serverConnector.awaitClosed(1000L);

        final AtomicBoolean closed = new AtomicBoolean();
        ClientConnector clientConnector = new StandardAsyncClientConnector(null, threadPool)
        {
            @Override
            public void close()
            {
                closed.set(true);
                super.close();
            }
        };
        try
        {
            clientConnector.connect(address);
            fail();
        }
        catch (RuntimeSocketConnectException x)
        {
            // Expected
        }
        assertTrue(closed.get());
    }
}
