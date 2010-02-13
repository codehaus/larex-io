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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.codehaus.larex.io.CallerBlocksPolicy;
import org.codehaus.larex.io.ThreadLocalByteBuffers;
import org.codehaus.larex.io.connector.ServerConnector;
import org.codehaus.larex.io.connector.async.StandardAsyncServerConnector;

/**
 * @version $Revision: 13 $ $Date$
 */
public class EchoServerMain
{
    public static void main(String[] args) throws Exception
    {
        int maxThreads = 500;
        ExecutorService threadPool = new ThreadPoolExecutor(0, maxThreads, 60L, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), new CallerBlocksPolicy());
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(null), 8850);
        AsyncInterpreterFactory interpreterFactory = new AsyncInterpreterFactory()
        {
            public AsyncInterpreter newAsyncInterpreter(AsyncCoordinator coordinator)
            {
                return new EchoAsyncInterpreter(coordinator);
            }
        };
        ServerConnector connector = new StandardAsyncServerConnector(address, interpreterFactory, threadPool, new ThreadLocalByteBuffers());
        connector.listen();
    }
}
