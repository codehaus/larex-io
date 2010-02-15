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
import java.util.concurrent.ScheduledExecutorService;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.Channel;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
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
 * @version $Revision$ $Date$
 */
public class StandardEndpoint<T extends Connection> extends Endpoint<T>
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final SocketChannel channel;
    private final Selector selector;
    private final ConnectionFactory<T> connectionFactory;
    private final ByteBuffers byteBuffers;
    private final Executor threadPool;
    private final ScheduledExecutorService scheduler;

    public StandardEndpoint(SocketChannel channel, Selector selector, ConnectionFactory<T> connectionFactory, ByteBuffers byteBuffers, Executor threadPool, ScheduledExecutorService scheduler)
    {
        this.channel = channel;
        this.selector = selector;
        this.connectionFactory = connectionFactory;
        this.byteBuffers = byteBuffers;
        this.threadPool = threadPool;
        this.scheduler = scheduler;
    }

    @Override
    public T connect(InetSocketAddress address)
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
            int connectTimeout = getConnectTimeout();
            if (connectTimeout < 0)
                connectTimeout = 0;
            logger.debug("{} connecting to {} (timeout {})", new Object[]{this, address, connectTimeout});
            socket.connect(address, connectTimeout);
            logger.debug("{} connected to {}", this, address);
            return connected(channel);
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

    protected T connected(SocketChannel channel) throws IOException
    {
        channel.configureBlocking(false);

        Coordinator coordinator = newCoordinator(selector, threadPool, scheduler);

        Channel asyncChannel = newAsyncChannel(channel, coordinator, byteBuffers);
        coordinator.setAsyncChannel(asyncChannel);

        T connection = connectionFactory.newConnection(coordinator);
        coordinator.setConnection(connection);

        register(selector, asyncChannel, coordinator);

        return connection;
    }

    protected Coordinator newCoordinator(Selector selector, Executor threadPool, ScheduledExecutorService scheduler)
    {
        return new TimeoutCoordinator(selector, threadPool, scheduler, getReadTimeout(), getWriteTimeout());
    }

    protected Channel newAsyncChannel(SocketChannel channel, Coordinator coordinator, ByteBuffers byteBuffers)
    {
        return new StandardChannel(channel, coordinator, byteBuffers);
    }

    protected void register(Selector selector, Channel channel, Coordinator coordinator)
    {
        selector.register(channel, coordinator);
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
