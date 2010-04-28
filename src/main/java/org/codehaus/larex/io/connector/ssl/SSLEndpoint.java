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

package org.codehaus.larex.io.connector.ssl;

import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Coordinator;
import org.codehaus.larex.io.Scheduler;
import org.codehaus.larex.io.Selector;
import org.codehaus.larex.io.connector.StandardEndpoint;
import org.codehaus.larex.io.ssl.SSLInterceptor;

/**
 * TODO: handle set[Need|Want]ClientAuth (and other SSLEngine methods. maybe ?)
 * @version $Revision$ $Date$
 */
public class SSLEndpoint<T extends Connection> extends StandardEndpoint<T>
{
    private final SSLContext sslContext;
    private final ByteBuffers sslByteBuffers;
    private volatile SSLEngine sslEngine;

    public SSLEndpoint(Selector selector, ConnectionFactory<T> connectionFactory, ByteBuffers byteBuffers, Executor threadPool, Scheduler scheduler, SSLContext sslContext, ByteBuffers sslByteBuffers)
    {
        super(selector, connectionFactory, byteBuffers, threadPool, scheduler);
        this.sslContext = sslContext;
        this.sslByteBuffers = sslByteBuffers;
    }

    @Override
    protected T newConnection(Coordinator coordinator)
    {
        String host = getSocketChannel().socket().getInetAddress().getHostAddress();
        int port = getSocketChannel().socket().getPort();
        sslEngine = sslContext.createSSLEngine(host, port);
        sslEngine.setUseClientMode(true);
        coordinator.addInterceptor(new SSLInterceptor(sslByteBuffers, sslEngine, coordinator));
        return super.newConnection(coordinator);
    }

    public SSLEngine getSSLEngine()
    {
        return sslEngine;
    }
}
