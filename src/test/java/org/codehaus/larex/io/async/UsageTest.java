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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.codehaus.larex.io.connector.Endpoint;
import org.codehaus.larex.io.connector.StandardClientConnector;
import org.junit.Test;

/**
 * @version $Revision$ $Date$
 */
public class UsageTest
{
    @Test
    public void testAsyncUsage()
    {
        InetSocketAddress address = null;
//        AsyncClientConnector connector = new StandardAsyncClientConnector(threadPool);
        Executor threadPool = null;
        StandardClientConnector connector = new StandardClientConnector(threadPool);
        // Default configuration for all connections
//        connector.setBindAddress(address);
//        connector.setConnectTimeout(1000L);
//        connector.setReadTimeout(1000L);
//        connector.setWriteTimeout(1000L);
        AsyncInterpreterFactory<AbstractAsyncInterpreter> interpreterFactory = null;
        // Class "connection" can be shared with the sync package ?
        Endpoint<AbstractAsyncInterpreter> endpoint = connector.newEndpoint(interpreterFactory);
        endpoint.setBindAddress(address);
        endpoint.setConnectTimeout(1000);
        endpoint.setReadTimeout(1000);
        endpoint.setWriteTimeout(1000);
        AbstractAsyncInterpreter connection = endpoint.connect(address);
        ByteBuffer buffer = null;
        connection.write(buffer);
        connection.close();

//        // What about only having a connection, just like socket ?
//        ClientConnector c = new StandardAsyncClientConnector(/*asyncselectors, threadpool => global*//*,asynclistener => local*/);
//        c.setBindAddress();
//        c.connect(address);
//        c.close();

        // Given a scenario where we have 1 JVM, 1 thread pool, 1 asyncselectormanager
        // then we need a way to create connections to A) different addresses; B) with different protocols (ie asyncinterpreters)
        // Ideally: 1 selector loop thread; N read threads from the thread pool
    }
}
