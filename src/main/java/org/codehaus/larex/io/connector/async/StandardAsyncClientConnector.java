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
import org.codehaus.larex.io.async.Channel;
import org.codehaus.larex.io.async.Connection;
import org.codehaus.larex.io.async.ConnectionFactory;
import org.codehaus.larex.io.async.Coordinator;
import org.codehaus.larex.io.async.ReadWriteSelector;
import org.codehaus.larex.io.async.Selector;
import org.codehaus.larex.io.async.StandardChannel;
import org.codehaus.larex.io.async.StandardCoordinator;
import org.codehaus.larex.io.connector.ClientConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardAsyncClientConnector implements ClientConnector
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final ConnectionFactory connectionFactory;
    private final Executor threadPool;
    private final Selector selector;
    private final SocketChannel channel;
    private volatile ByteBuffers byteBuffers = new ThreadLocalByteBuffers();
    private volatile Coordinator coordinator;

    public StandardAsyncClientConnector(ConnectionFactory connectionFactory, Executor threadPool)
    {
        try
        {
            this.connectionFactory = connectionFactory;
            this.threadPool = threadPool;
            selector = new ReadWriteSelector();
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

        Channel asyncChannel = newAsyncChannel(channel, coordinator);
        coordinator.setAsyncChannel(asyncChannel);

        Connection connection = connectionFactory.newConnection(coordinator);
        coordinator.setConnection(connection);

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

    protected Coordinator newCoordinator()
    {
        return new StandardCoordinator(selector, getThreadPool());
    }

    protected Channel newAsyncChannel(SocketChannel channel, Coordinator coordinator)
    {
        return new StandardChannel(channel, coordinator, byteBuffers);
    }

    protected void register(Channel channel, Coordinator coordinator)
    {
        selector.register(channel, coordinator);
    }
}
