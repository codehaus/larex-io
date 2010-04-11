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
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.StandardClientConnector;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @version $Revision: 903 $ $Date$
 */
public class ServerAcceptsClosesClientIsNotifiedTest extends AbstractTestCase
{
    @Test
    public void testCloseAfterAccept() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);

        final CountDownLatch registerLatch = new CountDownLatch(1);
        StandardServerConnector serverConnector = new StandardServerConnector(address, new EchoConnection.Factory(), getThreadPool(), getScheduler())
        {
            @Override
            protected Channel newChannel(SocketChannel channel, Coordinator coordinator)
            {
                return new StandardChannel(channel, coordinator)
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
                            registerLatch.countDown();
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
            final CountDownLatch closeLatch = new CountDownLatch(1);
            StandardClientConnector connector = new StandardClientConnector(getThreadPool(), getScheduler());
            Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new StandardConnection(coordinator)
                    {
                        @Override
                        protected void read(ByteBuffer buffer)
                        {
                        }

                        @Override
                        public void onRemoteClose()
                        {
                            closeLatch.countDown();
                        }
                    };
                }
            });
            endpoint.connect(new InetSocketAddress("localhost", port));

            // Wait for the Selector to register the channel
            assertTrue(registerLatch.await(1000, TimeUnit.MILLISECONDS));

            // When the server side of a socket is closed, the client is not notified (although a TCP FIN packet arrives).
            // Calling socket.isClosed() yields false, socket.isConnected() yields true, so no help.
            // Writing on the output stream causes a RST from the server, but this may not be converted to
            // a SocketException("Broken Pipe") depending on the TCP buffers on the OS, I think.
            // If the write is big enough, eventually SocketException("Broken Pipe") is raised.
            // The only reliable way is to read and check if we get -1.

            assertTrue(closeLatch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000L);
        }
    }
}
