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

package org.codehaus.larex.io.connector.async;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.RuntimeSocketConnectException;
import org.codehaus.larex.io.ThreadLocalByteBuffers;
import org.codehaus.larex.io.async.AsyncChannel;
import org.codehaus.larex.io.async.AsyncCoordinator;
import org.codehaus.larex.io.async.AsyncInterpreter;
import org.codehaus.larex.io.async.AsyncInterpreterFactory;
import org.codehaus.larex.io.async.AsyncSelector;
import org.codehaus.larex.io.async.ReadWriteAsyncSelector;
import org.codehaus.larex.io.async.StandardAsyncChannel;
import org.codehaus.larex.io.async.StandardAsyncCoordinator;
import org.codehaus.larex.io.connector.ClientConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardAsyncClientConnector implements ClientConnector
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final AsyncInterpreterFactory interpreterFactory;
    private final Executor threadPool;
    private final AsyncSelector selector;
    private final SocketChannel channel;
    private volatile ByteBuffers byteBuffers = new ThreadLocalByteBuffers();
    private volatile AsyncCoordinator coordinator;

    public StandardAsyncClientConnector(AsyncInterpreterFactory interpreterFactory, Executor threadPool)
    {
        try
        {
            this.interpreterFactory = interpreterFactory;
            this.threadPool = threadPool;
            selector = new ReadWriteAsyncSelector();
            channel = SocketChannel.open();
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    public void setByteBuffers(ByteBuffers byteBuffers)
    {
        this.byteBuffers = byteBuffers;
    }

    public void connect(InetSocketAddress address) throws RuntimeSocketConnectException
    {
        try
        {
            logger.debug("ClientConnector {} connecting to {}", this, address);
            // TODO: add bind address
            channel.socket().connect(address); // TODO: add timeout
            logger.debug("ClientConnector {} connected to {}", this, address);
            connected(channel);
        }
        catch (AlreadyConnectedException x)
        {
            close();
            throw new IllegalStateException(x);
        }
        catch (ConnectException x)
        {
            close();
            throw new RuntimeSocketConnectException(x);
        }
        catch (IOException x)
        {
            close();
            throw new RuntimeIOException(x);
        }
    }

    protected void connected(SocketChannel channel) throws IOException
    {
        channel.configureBlocking(false);

        coordinator = newCoordinator();

        AsyncChannel asyncChannel = newAsyncChannel(channel, coordinator);
        coordinator.setAsyncChannel(asyncChannel);

        AsyncInterpreter interpreter = interpreterFactory.newAsyncInterpreter(coordinator);
        coordinator.setAsyncInterpreter(interpreter);

        register(asyncChannel, coordinator);
    }

    public void close()
    {
        try
        {
            logger.debug("ClientConnector {} closing", this);
            if (coordinator != null)
                coordinator.close();
            channel.close();
            selector.close();
            logger.debug("ClientConnector {} closed", this);
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    public boolean awaitClosed(long timeout) throws InterruptedException
    {
        return selector.join(timeout);
    }

    protected Executor getThreadPool()
    {
        return threadPool;
    }

    protected AsyncCoordinator newCoordinator()
    {
        return new StandardAsyncCoordinator(selector, getThreadPool());
    }

    protected AsyncChannel newAsyncChannel(SocketChannel channel, AsyncCoordinator coordinator)
    {
        return new StandardAsyncChannel(channel, coordinator, byteBuffers);
    }

    protected void register(AsyncChannel channel, AsyncCoordinator coordinator)
    {
        selector.register(channel, coordinator);
    }
}
