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

import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.StandardClientConnector;
import org.codehaus.larex.io.connector.StandardServerConnector;
import org.junit.Test;

/**
 * @version $Revision$ $Date$
 */
public class ClientClosesServerIsNotifiedTest extends AbstractTestCase
{
    @Test
    public void testClientClosesServerIsNotified() throws Exception
    {
        final CountDownLatch closeLatch = new CountDownLatch(1);
        StandardServerConnector serverConnector = new StandardServerConnector(new InetSocketAddress("localhost", 0), new ConnectionFactory()
        {
            public Connection newConnection(Coordinator coordinator)
            {
                return new IdleConnection(coordinator)
                {
                    @Override
                    public void onRemoteClose()
                    {
                        closeLatch.countDown();
                    }
                };
            }
        }, getThreadPool(), getScheduler());
        int port = serverConnector.listen();

        try
        {
            final CountDownLatch openLatch = new CountDownLatch(1);
            StandardClientConnector connector = new StandardClientConnector(getThreadPool(), getScheduler());
            Endpoint<StandardConnection> endpoint = connector.newEndpoint(new ConnectionFactory<StandardConnection>()
            {
                public StandardConnection newConnection(Coordinator coordinator)
                {
                    return new IdleConnection(coordinator)
                    {
                        @Override
                        public void onReady()
                        {
                            openLatch.countDown();
                        }
                    };
                }
            });
            StandardConnection connection = endpoint.connect(new InetSocketAddress("localhost", port));
            openLatch.await(1000, TimeUnit.MILLISECONDS);
            connection.close();
            closeLatch.await(1000, TimeUnit.MILLISECONDS);
        }
        finally
        {
            serverConnector.close();
            serverConnector.join(1000);
        }
    }
}
