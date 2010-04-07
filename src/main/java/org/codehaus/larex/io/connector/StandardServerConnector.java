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
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.Channel;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Coordinator;
import org.codehaus.larex.io.ReadWriteSelector;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.Scheduler;
import org.codehaus.larex.io.Selector;
import org.codehaus.larex.io.StandardChannel;
import org.codehaus.larex.io.ThreadLocalByteBuffers;
import org.codehaus.larex.io.TimeoutCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardServerConnector
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final InetSocketAddress address;
    private final ConnectionFactory connectionFactory;
    private final Executor threadPool;
    private final Scheduler scheduler;
    private final ByteBuffers byteBuffers;
    private final Selector[] selectors;
    private final AtomicInteger selector = new AtomicInteger();
    private volatile boolean reuseAddress = true;
    private volatile int backlogSize = 128;
    private volatile long readTimeout = 0;
    private volatile long writeTimeout = 0;
    private volatile Thread acceptorThread;
    private volatile ServerSocketChannel serverChannel;

    public StandardServerConnector(InetSocketAddress address, ConnectionFactory connectionFactory, Executor threadPool, Scheduler scheduler)
    {
        this(address, connectionFactory, threadPool, scheduler, 1);
    }

    public StandardServerConnector(InetSocketAddress address, ConnectionFactory connectionFactory, Executor threadPool, Scheduler scheduler, int selectors)
    {
        this.address = address;
        this.connectionFactory = connectionFactory;
        this.threadPool = threadPool;
        this.scheduler = scheduler;
        this.byteBuffers = newByteBuffers();
        if (selectors < 1)
            throw new IllegalArgumentException("Invalid selectors count " + selectors + ": must be >= 1");
        this.selectors = new Selector[selectors];
        for (int i = 0; i < selectors; ++i)
            this.selectors[i] = newAsyncSelector();
    }

    protected ByteBuffers newByteBuffers()
    {
        return new ThreadLocalByteBuffers();
    }

    protected Selector newAsyncSelector()
    {
        return new ReadWriteSelector();
    }

    public Boolean isReuseAddress()
    {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress)
    {
        this.reuseAddress = reuseAddress;
    }

    public Integer getBacklogSize()
    {
        return backlogSize;
    }

    public void setBacklogSize(int backlogSize)
    {
        this.backlogSize = backlogSize;
    }

    public long getReadTimeout()
    {
        return readTimeout;
    }

    public void setReadTimeout(long readTimeout)
    {
        this.readTimeout = readTimeout;
    }

    public long getWriteTimeout()
    {
        return writeTimeout;
    }

    public void setWriteTimeout(long writeTimeout)
    {
        this.writeTimeout = writeTimeout;
    }

    public int listen() throws RuntimeIOException
    {
        try
        {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(true);
            serverChannel.socket().setReuseAddress(isReuseAddress());
            serverChannel.socket().bind(address, getBacklogSize());
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }

        // TODO: use more acceptor threads ?
        // TODO: use a thread factory ?
        threadPool.execute(new Acceptor());

        logger.info("ServerConnector {} listening on {}", this, serverChannel.socket().getLocalSocketAddress());
        return serverChannel.socket().getLocalPort();
    }

    public void close()
    {
        logger.debug("ServerConnector {} closing", this);
        try
        {
            for (Selector selector : selectors)
                selector.close();
            serverChannel.close();
            logger.debug("ServerConnector {} closed", this);
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    public boolean join(long timeout) throws InterruptedException
    {
        acceptorThread.join(timeout);
        return !acceptorThread.isAlive();
    }

    protected void accepted(SocketChannel channel) throws IOException
    {
        channel.configureBlocking(false);

        Selector selector = chooseSelector(selectors);

        Coordinator coordinator = newCoordinator(selector, threadPool, scheduler);

        Channel asyncChannel = newAsyncChannel(channel, coordinator, byteBuffers);
        coordinator.setAsyncChannel(asyncChannel);

        Connection connection = connectionFactory.newConnection(coordinator);
        coordinator.setConnection(connection);

        register(selector, asyncChannel, coordinator);
    }

    protected Selector chooseSelector(Selector[] selectors)
    {
        int index = selector.incrementAndGet();
        index = Math.abs(index % selectors.length);
        return selectors[index];
    }

    protected Coordinator newCoordinator(Selector selector, Executor threadPool, Scheduler scheduler)
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

    protected class Acceptor implements Runnable
    {
        public void run()
        {
            try
            {
                acceptorThread = Thread.currentThread();
                logger.info("ServerConnector {}, acceptor loop entered", this);

                while (serverChannel.isOpen())
                {
                    try
                    {
                        // Do not use the selector for accept() operation, as it is more expensive
                        // (for each new connection needs to return from select, and then accept())
                        SocketChannel socketChannel = serverChannel.accept();

                        // If this server connector is closed but this thread is still active
                        // we should avoid processing the connection
                        if (serverChannel.isOpen())
                        {
                            logger.debug("ServerConnector {}, accepted socket {}", this, socketChannel);
                            accepted(socketChannel);
                        }
                    }
                    catch (SocketTimeoutException x)
                    {
                        logger.debug("ServerConnector {}, ignoring timeout during accept", this);
                    }
                    catch (AsynchronousCloseException x)
                    {
                        logger.debug("ServerConnector {} closed asynchronously", this);
                        break;
                    }
                    catch (IOException x)
                    {
                        close();
                        throw new RuntimeIOException(x);
                    }
                }
            }
            finally
            {
                logger.info("ServerConnector {}, acceptor loop exited", this);
            }
        }
    }
}
