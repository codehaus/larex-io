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
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.async.AsyncChannel;
import org.codehaus.larex.io.async.AsyncCoordinator;
import org.codehaus.larex.io.async.AsyncInterpreter;
import org.codehaus.larex.io.async.AsyncInterpreterFactory;
import org.codehaus.larex.io.async.AsyncSelector;
import org.codehaus.larex.io.async.ReadWriteAsyncSelector;
import org.codehaus.larex.io.async.StandardAsyncChannel;
import org.codehaus.larex.io.async.StandardAsyncCoordinator;
import org.codehaus.larex.io.connector.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * // TODO: adding the number of selectors will move us towards a "connection" abstraction
 * @version $Revision: 903 $ $Date$
 */
public class StandardAsyncServerConnector implements ServerConnector
{
    private static final AtomicInteger ids = new AtomicInteger();

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final InetSocketAddress address;
    private final AsyncInterpreterFactory interpreterFactory;
    private final Executor threadPool;
    private final ByteBuffers byteBuffers;
    private final AsyncSelector[] selectors;
    private final AtomicInteger selector = new AtomicInteger();
    private volatile Boolean reuseAddress;
    private volatile Integer backlogSize;
    private volatile Thread acceptorThread;
    private volatile ServerSocketChannel serverChannel;

    public StandardAsyncServerConnector(InetSocketAddress address, AsyncInterpreterFactory interpreterFactory, Executor threadPool, ByteBuffers byteBuffers)
    {
        this(address, interpreterFactory, threadPool, byteBuffers, 1);
    }
    public StandardAsyncServerConnector(InetSocketAddress address, AsyncInterpreterFactory interpreterFactory, Executor threadPool, ByteBuffers byteBuffers, int selectors)
    {
        this.address = address;
        this.interpreterFactory = interpreterFactory;
        this.threadPool = threadPool;
        this.byteBuffers = byteBuffers;
        if (selectors < 1)
            throw new IllegalArgumentException("Invalid selectors count " + selectors + ": must be >= 1");
        this.selectors = new AsyncSelector[selectors];
        for (int i = 0; i < selectors; ++i)
            this.selectors[i] = newAsyncSelector();
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

    public int listen() throws RuntimeIOException
    {
        initializeDefaults();

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

        threadPool.execute(new Acceptor()); // TODO: use thread factory ?

        return serverChannel.socket().getLocalPort();
    }

    protected AsyncSelector newAsyncSelector()
    {
        return new ReadWriteAsyncSelector();
    }

    private void initializeDefaults()
    {
        if (reuseAddress == null)
            reuseAddress = true;

        if (backlogSize == null)
            backlogSize = 128;
        if (backlogSize <= 0)
            throw new IllegalArgumentException("Illegal backlog size " + backlogSize + ": must be positive");
    }

    public void close()
    {
        logger.debug("ServerConnector {} closing", this);
        try
        {
            for (AsyncSelector selector : selectors)
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

        // Pick a selector
        int index = selector.incrementAndGet();
        index = Math.abs(index % selectors.length);
        AsyncSelector selector = selectors[index];

        AsyncCoordinator coordinator = newCoordinator(selector, threadPool);

        AsyncChannel asyncChannel = newAsyncChannel(channel, coordinator, byteBuffers);
        coordinator.setAsyncChannel(asyncChannel);

        AsyncInterpreter interpreter = interpreterFactory.newAsyncInterpreter(coordinator);
        coordinator.setAsyncInterpreter(interpreter);

        register(selector, asyncChannel, coordinator);
    }

    protected AsyncCoordinator newCoordinator(AsyncSelector selector, Executor threadPool)
    {
        return new StandardAsyncCoordinator(selector, threadPool);
    }

    protected AsyncChannel newAsyncChannel(SocketChannel channel, AsyncCoordinator coordinator, ByteBuffers byteBuffers)
    {
        return new StandardAsyncChannel(channel, coordinator, byteBuffers);
    }

    protected void register(AsyncSelector selector, AsyncChannel channel, AsyncCoordinator coordinator)
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
