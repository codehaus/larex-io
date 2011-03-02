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
import java.util.concurrent.TimeUnit;

import org.codehaus.larex.io.connector.ClientConnector;
import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.ServerConnector;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardClientConnectorTest extends AbstractTestCase
{
    @Test
    public void testConnect() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);

        final CountDownLatch acceptLatch = new CountDownLatch(1);
        ServerConnector serverConnector = new ServerConnector(address, new EchoConnection.Factory(), getThreadPool())
        {
            @Override
            protected void accepted(SocketChannel socketChannel) throws IOException
            {
                super.accepted(socketChannel);
                acceptLatch.countDown();
            }
        };
        int port = serverConnector.listen();

        try
        {
            final CountDownLatch responseLatch = new CountDownLatch(1);
            ClientConnector clientConnector = new ClientConnector(getThreadPool());
            clientConnector.open();
            try
            {
                Endpoint<StandardConnection> endpoint = clientConnector.newEndpoint(new ConnectionFactory<StandardConnection>()
                {
                    public StandardConnection newConnection(Controller controller)
                    {
                        return new StandardConnection(controller)
                        {
                            @Override
                            protected void onRead(ByteBuffer buffer)
                            {
                                responseLatch.countDown();
                            }
                        };
                    }
                });

                StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
                assertTrue(connection.awaitOpened(1000));
                try
                {
                    assertTrue(acceptLatch.await(1000, TimeUnit.MILLISECONDS));
                    connection.flush(ByteBuffer.wrap(new byte[]{1}));
                    assertTrue(responseLatch.await(1000, TimeUnit.MILLISECONDS));
                }
                finally
                {
                    connection.softClose(1000);
                }
            }
            finally
            {
                clientConnector.close();
                clientConnector.join(1000);
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000L);
        }
    }
}
