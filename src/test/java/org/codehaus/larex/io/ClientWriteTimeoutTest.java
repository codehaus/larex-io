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
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.connector.ClientConnector;
import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.ServerConnector;
import org.codehaus.larex.io.connector.StandardEndpoint;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @version $Revision$ $Date$
 */
public class ClientWriteTimeoutTest extends AbstractTestCase
{
    @Test
    public void testWriteTimeout() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        ServerConnector serverConnector = new ServerConnector(address, new StandardConnection.Factory(), getThreadPool(), getScheduler());
        int port = serverConnector.listen();
        try
        {
            ClientConnector connector = new ClientConnector(getThreadPool(), getScheduler())
            {
                @Override
                public <T extends Connection> Endpoint<T> newEndpoint(ConnectionFactory<T> connectionFactory)
                {
                    return new StandardEndpoint<T>(connectionFactory, chooseSelector(), getByteBuffers(), getThreadPool(), getScheduler())
                    {
                        @Override
                        protected Coordinator newCoordinator()
                        {
                            return new TimeoutCoordinator(getSelector(), getByteBuffers(), getThreadPool(), getScheduler(), getReadTimeout(), getWriteTimeout())
                            {
                                public AtomicInteger writes = new AtomicInteger(0);

                                @Override
                                public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
                                {
                                    if (writes.addAndGet(1) == 1)
                                    {
                                        // Write only one byte
                                        ByteBuffer newBuffer = ByteBuffer.wrap(new byte[]{buffer.get()});
                                        return super.write(newBuffer);
                                    }
                                    else
                                    {
                                        return 0;
                                    }
                                }

                                @Override
                                public void onWriteReady()
                                {
                                    if (writes.get() > 1)
                                    {
                                        // Block write notifications, so that the test can timeout
                                        needsWrite(false);
                                    }
                                    else
                                    {
                                        super.onWriteReady();
                                    }
                                }
                            };
                        }
                    };
                }
            };
            Endpoint<StandardConnection> endpoint = connector.newEndpoint(new StandardConnection.Factory());
            endpoint.setWriteTimeout(1000);
            StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
            assertTrue(connection.awaitReady(1000));

            try
            {
                try
                {
                    connection.flush(ByteBuffer.wrap(new byte[]{1, 2}));
                    fail();
                }
                catch (RuntimeSocketTimeoutException x)
                {
                    // Test passed
                }
            }
            finally
            {
                connection.softClose(1000);
            }
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000);
        }
    }
}
