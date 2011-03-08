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
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.codehaus.larex.io.connector.ClientConnector;
import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.ServerConnector;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class ServerAcceptsClosesClientIsNotifiedTest extends AbstractTestCase
{
    @Test
    public void testCloseAfterAccept() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);

        final CountDownLatch registerLatch = new CountDownLatch(1);
        ServerConnector serverConnector = new ServerConnector(address, new EchoConnection.Factory(), getThreadPool())
        {
            @Override
            protected Channel newChannel(Reactor reactor, SocketChannel channel, Controller controller)
            {
                return new StandardChannel(reactor, channel, controller)
                {
                    @Override
                    public boolean register(Selector selector, Reactor.Listener listener) throws RuntimeSocketClosedException
                    {
                        try
                        {
                            return super.register(selector, listener);
                        }
                        finally
                        {
                            registerLatch.countDown();
                        }
                    }
                };
            }

            @Override
            protected void register(Reactor reactor, Channel channel, Reactor.Listener listener)
            {
                channel.close(StreamType.INPUT_OUTPUT);
                super.register(reactor, channel, listener);
            }
        };
        int port = serverConnector.listen();

        try
        {
            final CountDownLatch closeLatch = new CountDownLatch(1);
            ClientConnector connector = new ClientConnector(getThreadPool());
            connector.open();
            try
            {
                Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
                {
                    public StandardConnection newConnection(Controller controller)
                    {
                        return new StandardConnection(controller)
                        {
                            @Override
                            public void onRemoteClose()
                            {
                                closeLatch.countDown();
                            }
                        };
                    }
                });
                endpoint.connect(new InetSocketAddress("localhost", port));

                // Wait for the Reactor to register the channel
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
