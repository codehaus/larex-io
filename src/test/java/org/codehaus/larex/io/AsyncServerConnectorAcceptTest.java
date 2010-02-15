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
public class AsyncServerConnectorAcceptTest
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
    public void testBlockingAccept() throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);

        final CountDownLatch latch = new CountDownLatch(1);
        StandardServerConnector serverConnector = new StandardServerConnector(address, null, threadPool, scheduler)
        {
            @Override
            protected void accepted(SocketChannel channel) throws IOException
            {
                latch.countDown();
            }
        };
        int port = serverConnector.listen();
        try
        {
            Socket socket = new Socket(address.getHostName(), port);
            try
            {
                Assert.assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
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
