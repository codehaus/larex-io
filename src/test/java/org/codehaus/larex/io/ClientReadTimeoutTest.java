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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.codehaus.larex.io.connector.ClientConnector;
import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.ServerConnector;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @version $Revision$ $Date$
 */
public class ClientReadTimeoutTest extends AbstractTestCase
{
    @Test
    public void testReadTimeout() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        ServerConnector serverConnector = new ServerConnector(address, new EchoConnection.Factory(), getThreadPool(), getScheduler());
        int port = serverConnector.listen();
        try
        {
            final CountDownLatch timeoutLatch = new CountDownLatch(1);
            ClientConnector connector = new ClientConnector(getThreadPool(), getScheduler());
            Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new StandardConnection(coordinator)
                    {
                        @Override
                        public void onReadTimeout()
                        {
                            timeoutLatch.countDown();
                        }
                    };
                }
            });
            int readTimeout = 1000;
            endpoint.setReadTimeout(readTimeout);
            StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
            try
            {
                assertTrue(timeoutLatch.await(readTimeout * 2, TimeUnit.MILLISECONDS));
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
