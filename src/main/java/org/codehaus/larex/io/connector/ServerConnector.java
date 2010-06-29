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
import org.codehaus.larex.io.CachedByteBuffers;
import org.codehaus.larex.io.Channel;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.Coordinator;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.Selector;
import org.codehaus.larex.io.StandardChannel;
import org.codehaus.larex.io.TimeoutCoordinator;
import org.codehaus.larex.io.TimeoutReadWriteSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ServerConnector
{
    private static final AtomicInteger ids = new AtomicInteger();

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final InetSocketAddress address;
    private final ConnectionFactory connectionFactory;
    private final Executor threadPool;
    private final AtomicInteger selectorIndex = new AtomicInteger();
    private volatile ByteBuffers byteBuffers;
    private volatile int selectorCount = 1;
    private volatile Selector[] selectors;
    private volatile int acceptorCount = 1;
    private volatile Thread[] acceptors;
    private volatile boolean reuseAddress = true;
    private volatile int backlogSize = 128;
    private volatile long readTimeout = 0;
    private volatile long writeTimeout = 0;
    private volatile ServerSocketChannel serverChannel;

    public ServerConnector(InetSocketAddress address, ConnectionFactory connectionFactory, Executor threadPool)
    {
        this.address = address;
        this.connectionFactory = connectionFactory;
        this.threadPool = threadPool;
    }

    protected ByteBuffers newByteBuffers()
    {
        return new CachedByteBuffers();
    }

    public Executor getThreadPool()
    {
        return threadPool;
    }

    protected ByteBuffers getByteBuffers()
    {
        return byteBuffers;
    }

    protected Selector[] getSelectors()
    {
        return selectors;
    }

    public int getSelectorCount()
    {
        return selectorCount;
    }

    public void setSelectorCount(int selectorCount)
    {
        this.selectorCount = selectorCount;
    }

    public int getAcceptorCount()
    {
        return acceptorCount;
    }

    public void setAcceptorCount(int acceptorCount)
    {
        this.acceptorCount = acceptorCount;
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

        this.byteBuffers = newByteBuffers();

        this.selectors = new Selector[getSelectorCount()];
        for (int i = 0; i < selectors.length; ++i)
            this.selectors[i] = newSelector();

        this.acceptors = new Thread[getAcceptorCount()];
        for (int i = 0; i < acceptors.length; ++i)
        {
            Thread thread = newAcceptorThread(new Acceptor());
            thread.start();
            this.acceptors[i] = thread;
        }

        logger.info("ServerConnector {} listening on {}", this, serverChannel.socket().getLocalSocketAddress());
        return serverChannel.socket().getLocalPort();
    }

    protected Selector newSelector()
    {
        TimeoutReadWriteSelector selector = new TimeoutReadWriteSelector();
        selector.open();
        return selector;
    }

    protected Thread newAcceptorThread(Runnable acceptor)
    {
        return new Thread(acceptor, "Acceptor-" + ids.incrementAndGet());
    }

    public void close()
    {
        logger.debug("ServerConnector {} closing", this);
        try
        {
            for (Thread acceptor : acceptors)
                acceptor.interrupt();

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
        boolean result = true;

        for (Thread acceptor : acceptors)
        {
            acceptor.join(timeout);
            result &= acceptor.isAlive();
        }

        for (Selector selector : selectors)
            result &= selector.join(timeout);

        return result;
    }

    protected void accept()
    {
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

    protected void accepted(SocketChannel socketChannel) throws IOException
    {
        socketChannel.configureBlocking(false);

        Selector selector = chooseSelector();
        Coordinator coordinator = newCoordinator(selector);

        Channel channel = newChannel(selector, socketChannel, coordinator);
        coordinator.setChannel(channel);

        Connection connection = newConnection(socketChannel, coordinator);
        coordinator.setConnection(connection);

        register(selector, channel, coordinator);
    }

    protected Selector chooseSelector()
    {
        int index = selectorIndex.incrementAndGet();
        Selector[] selectors = getSelectors();
        index = Math.abs(index % selectors.length);
        return selectors[index];
    }

    protected Coordinator newCoordinator(Selector selector)
    {
        return new TimeoutCoordinator(selector, getByteBuffers(), getThreadPool(), getReadTimeout(), getWriteTimeout());
    }

    protected Channel newChannel(Selector selector, SocketChannel channel, Controller controller)
    {
        return new StandardChannel(selector, channel, controller);
    }

    protected Connection newConnection(SocketChannel socketChannel, Controller controller)
    {
        return connectionFactory.newConnection(controller);
    }

    protected void register(Selector selector, Channel channel, Selector.Listener listener)
    {
        selector.register(channel, listener);
    }

    protected class Acceptor implements Runnable
    {
        public void run()
        {
            logger.debug("ServerConnector {}, acceptor loop entered", this);
            try
            {
                accept();
            }
            finally
            {
                logger.debug("ServerConnector {}, acceptor loop exited", this);
            }
        }
    }
}
