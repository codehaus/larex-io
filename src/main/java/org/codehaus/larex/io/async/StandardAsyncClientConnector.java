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

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

import org.codehaus.larex.io.ClientConnector;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.RuntimeSocketConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardAsyncClientConnector implements ClientConnector
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final AsyncConnectorListener listener;
    private final Executor threadPool;
    private final SelectorManager selector;
    private final SocketChannel channel;
    private volatile AsyncCoordinator coordinator;

    public StandardAsyncClientConnector(AsyncConnectorListener listener, Executor threadPool)
    {
        try
        {
            this.listener = listener;
            this.threadPool = threadPool;
            selector = new ReadWriteSelectorManager(threadPool);
            channel = SocketChannel.open();
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
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

        AsyncEndpoint endpoint = newEndpoint(channel, coordinator);
        coordinator.setEndpoint(endpoint);

        AsyncInterpreter interpreter = listener.connected(coordinator);
        coordinator.setInterpreter(interpreter);

        register(endpoint, coordinator);
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
        return selector.awaitClosed(timeout);
    }

    protected Executor getThreadPool()
    {
        return threadPool;
    }

    protected AsyncCoordinator newCoordinator()
    {
        return new StandardAsyncCoordinator(selector, getThreadPool());
    }

    protected AsyncEndpoint newEndpoint(SocketChannel channel, AsyncCoordinator coordinator)
    {
        return new StandardAsyncEndpoint(channel, coordinator);
    }

    protected void register(AsyncEndpoint endpoint, AsyncCoordinator coordinator)
    {
        selector.register(endpoint, coordinator);
    }
}
