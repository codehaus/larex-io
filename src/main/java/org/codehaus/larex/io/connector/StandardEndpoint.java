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

package org.codehaus.larex.io.connector;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.Channel;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.Coordinator;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.RuntimeSocketConnectException;
import org.codehaus.larex.io.RuntimeSocketTimeoutException;
import org.codehaus.larex.io.Selector;
import org.codehaus.larex.io.StandardChannel;
import org.codehaus.larex.io.TimeoutCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class StandardEndpoint<C extends Connection> extends Endpoint<C>
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SocketChannel channel;
    private final ConnectionFactory<C> connectionFactory;
    private final Selector selector;
    private final ByteBuffers byteBuffers;
    private final Executor threadPool;

    public StandardEndpoint(ConnectionFactory<C> connectionFactory, Selector selector, ByteBuffers byteBuffers, Executor threadPool)
    {
        try
        {
            this.channel = SocketChannel.open();
            this.connectionFactory = connectionFactory;
            this.selector = selector;
            this.byteBuffers = byteBuffers;
            this.threadPool = threadPool;
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    public SocketChannel getSocketChannel()
    {
        return channel;
    }

    protected Selector getSelector()
    {
        return selector;
    }

    protected ByteBuffers getByteBuffers()
    {
        return byteBuffers;
    }

    protected Executor getThreadPool()
    {
        return threadPool;
    }

    @Override
    public C connect(InetSocketAddress address)
    {
        try
        {
            Socket socket = channel.socket();
            InetSocketAddress bindAddress = getBindAddress();
            if (bindAddress != null)
            {
                socket.bind(bindAddress);
                logger.debug("{} bound to {}", this, bindAddress);
            }
            long connectTimeout = getConnectTimeout();
            if (connectTimeout < 0)
                connectTimeout = 0;
            logger.debug("{} connecting to {} (timeout {})", new Object[]{this, address, connectTimeout});
            socket.connect(address, Long.valueOf(connectTimeout).intValue());
            logger.debug("{} connected to {}", this, address);
            return connected();
        }
        catch (AlreadyConnectedException x)
        {
            close();
            throw x;
        }
        catch (ConnectException x)
        {
            close();
            throw new RuntimeSocketConnectException(x);
        }
        catch (SocketTimeoutException x)
        {
            close();
            throw new RuntimeSocketTimeoutException(x);
        }
        catch (IOException x)
        {
            close();
            throw new RuntimeIOException(x);
        }
    }

    protected C connected() throws IOException
    {
        getSocketChannel().configureBlocking(false);

        Coordinator coordinator = newCoordinator();

        Channel channel = newChannel(coordinator);
        coordinator.setChannel(channel);

        C connection = newConnection(coordinator);
        coordinator.setConnection(connection);

        register(channel, coordinator);

        return connection;
    }

    protected C newConnection(Controller controller)
    {
        return connectionFactory.newConnection(controller);
    }

    protected Coordinator newCoordinator()
    {
        return new TimeoutCoordinator(getSelector(), getByteBuffers(), getThreadPool(), getReadTimeout(), getWriteTimeout());
    }

    protected Channel newChannel(Controller controller)
    {
        return new StandardChannel(getSelector(), getSocketChannel(), controller);
    }

    protected void register(Channel channel, Selector.Listener listener)
    {
        getSelector().register(channel, listener);
    }

    private void close()
    {
        try
        {
            channel.close();
        }
        catch (IOException x)
        {
            logger.debug("Exception closing channel " + channel, x);
        }
    }
}
