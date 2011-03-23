/*
 * Copyright (c) 2011 the original author or authors.
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

package org.codehaus.larex.io.connector;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.codehaus.larex.io.BlockingConnection;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.RuntimeSocketClosedException;
import org.codehaus.larex.io.RuntimeSocketTimeoutException;
import org.codehaus.larex.io.StandardConnection;
import org.codehaus.larex.io.StreamType;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClientConnectorCloseTest extends ThreadPoolTest
{
    private ServerConnector server;
    private ClientConnector client;

    private ServerConnector startServer(ConnectionFactory factory)
    {
        server = new ServerConnector(new InetSocketAddress("localhost", 0), factory, createThreadPool("server"));
        server.listen();
        return server;
    }

    private ClientConnector startClient()
    {
        client = new ClientConnector(createThreadPool("client"));
        client.open();
        return client;
    }

    @After
    public void stopServer() throws Exception
    {
        if (client != null)
        {
            client.close();
            client.join(1000);
        }
        if (server != null)
        {
            server.close();
            server.join(1000);
        }
    }

    @Test
    public void testClientClosesInput() throws Exception
    {
        final CountDownLatch readLatch = new CountDownLatch(1);
        final CountDownLatch writeLatch = new CountDownLatch(1);
        class ServerConnection extends StandardConnection
        {
            ServerConnection(Controller controller)
            {
                super(controller);
            }

            @Override
            protected boolean onRead(ByteBuffer buffer)
            {
                try
                {
                    assertTrue(readLatch.await(1, TimeUnit.SECONDS));
                    write(buffer);
                    writeLatch.countDown();
                    return true;
                }
                catch (InterruptedException x)
                {
                    throw new RuntimeSocketTimeoutException(x);
                }
            }
        }

        ServerConnector server = startServer(new ConnectionFactory()
        {
            @Override
            public Connection newConnection(Controller controller)
            {
                return new ServerConnection(controller);
            }
        });

        ClientConnector client = startClient();
        BlockingConnection connection = client.buildConnection(new BlockingConnection.Factory()).connect(server.getAddress());
        assertTrue(connection.awaitOpened(1000));

        byte[] bytes = {1};
        connection.write(ByteBuffer.wrap(bytes));
        connection.close(StreamType.INPUT);
        readLatch.countDown();
        assertTrue(writeLatch.await(1, TimeUnit.SECONDS));

        try
        {
            connection.read(ByteBuffer.allocate(bytes.length));
            fail();
        }
        catch (RuntimeSocketClosedException e)
        {
            // Expected
        }
    }

    @Test
    public void testClientClosesOutput() throws Exception
    {
        final byte[] endBytes = "end".getBytes("UTF-8");
        class ServerConnection extends StandardConnection
        {
            ServerConnection(Controller controller)
            {
                super(controller);
            }

            @Override
            protected void onRemoteClose()
            {
                write(ByteBuffer.wrap(endBytes));
            }
        }

        ServerConnector server = startServer(new ConnectionFactory()
        {
            @Override
            public Connection newConnection(Controller controller)
            {
                return new ServerConnection(controller);
            }
        });

        ClientConnector client = startClient();
        BlockingConnection connection = client.buildConnection(new BlockingConnection.Factory()).connect(server.getAddress());
        assertTrue(connection.awaitOpened(1000));

        connection.close(StreamType.OUTPUT);
        ByteBuffer buffer = ByteBuffer.allocate(endBytes.length);
        int read = connection.read(buffer);
        buffer.flip();

        assertEquals(endBytes.length, read);
        assertEquals(endBytes.length, buffer.remaining());
        assertTrue(Arrays.equals(endBytes, buffer.array()));

        read = connection.read(ByteBuffer.allocate(0));

        assertEquals(-1, read);
    }
}
