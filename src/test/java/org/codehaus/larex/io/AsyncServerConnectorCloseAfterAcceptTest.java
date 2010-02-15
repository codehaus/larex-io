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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @version $Revision: 903 $ $Date$
 */
public class AsyncServerConnectorCloseAfterAcceptTest
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
    public void testCloseAfterAccept() throws Exception
    {
        InetAddress loopback = InetAddress.getByName(null);
        InetSocketAddress address = new InetSocketAddress(loopback, 0);

        ConnectionFactory connectionFactory = new ConnectionFactory()
        {
            public Connection newConnection(Coordinator coordinator)
            {
                return null;
            }
        };

        final CountDownLatch latch = new CountDownLatch(1);
        StandardServerConnector serverConnector = new StandardServerConnector(address, connectionFactory, threadPool, scheduler)
        {
            @Override
            protected Channel newAsyncChannel(SocketChannel channel, Coordinator coordinator, ByteBuffers byteBuffers)
            {
                return new StandardChannel(channel, coordinator, byteBuffers)
                {
                    @Override
                    public void register(java.nio.channels.Selector selector, Selector.Listener listener) throws RuntimeSocketClosedException
                    {
                        try
                        {
                            super.register(selector, listener);
                        }
                        finally
                        {
                            latch.countDown();
                        }
                    }
                };
            }

            @Override
            protected void register(Selector selector, Channel channel, Coordinator coordinator)
            {
                channel.close();
                super.register(selector, channel, coordinator);
            }
        };
        int port = serverConnector.listen();
        try
        {
            Socket socket = new Socket(loopback, port);
            try
            {
                // Wait for the Selector to register the channel
                Assert.assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));

                // When the server side of a socket is closed, the client is not notified (although a TCP FIN packet arrives).
                // Calling socket.isClosed() yields false, socket.isConnected() yields true, so no help.
                // Writing on the output stream causes a RST from the server, but this may not be converted to
                // a SocketException("Broken Pipe") depending on the TCP buffers on the OS, I think.
                // If the write is big enough, eventually SocketException("Broken Pipe") is raised.
                // The only reliable way is to read and check if we get -1.

                int datum = socket.getInputStream().read();
                Assert.assertEquals(-1, datum);
            }
            finally
            {
                socket.close();
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000L);
        }
    }
}
