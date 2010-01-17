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
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardAsyncServerConnector implements ServerConnector
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Lock closeLock = new ReentrantLock();
    private final Condition closeCondition = closeLock.newCondition();
    private final InetSocketAddress address;
    private final AsyncConnectorListener listener;
    private final Executor threadPool;
    private Boolean reuseAddress;
    private Integer backlogSize;
    private volatile ServerSocketChannel serverChannel;
    private volatile SelectorManager selector;
    private volatile State state = State.CLOSED;

    public StandardAsyncServerConnector(InetSocketAddress address, AsyncConnectorListener listener, Executor threadPool)
    {
        this.address = address;
        this.listener = listener;
        this.threadPool = threadPool;
    }

    public void setReuseAddress(boolean reuseAddress)
    {
        this.reuseAddress = reuseAddress;
    }

    public void setBacklogSize(int backlogSize)
    {
        this.backlogSize = backlogSize;
    }

    protected Executor getThreadPool()
    {
        return threadPool;
    }

    protected SelectorManager getSelector()
    {
        return selector;
    }

    public int listen() throws RuntimeIOException
    {
        state = State.OPEN;

        initializeDefaults();

        try
        {
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(true);
            serverChannel.socket().setReuseAddress(reuseAddress);
            serverChannel.socket().bind(address, backlogSize);
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }

        selector = new ReadWriteSelectorManager(threadPool);
        threadPool.execute(new AcceptWorker());

        return serverChannel.socket().getLocalPort();
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
            selector.close();
            serverChannel.close();
            logger.debug("ServerConnector {} closed", this);
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    public boolean awaitClosed(long timeout) throws InterruptedException
    {
        selector.awaitClosed(timeout);

        long nanos = TimeUnit.MILLISECONDS.toNanos(timeout);
        final Lock closeLock = this.closeLock;
        closeLock.lock();
        try
        {
            while (true)
            {
                if (state == State.CLOSED)
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = closeCondition.awaitNanos(nanos);
            }
        }
        finally
        {
            closeLock.unlock();
        }
    }

    protected void accepted(SocketChannel channel) throws IOException
    {
        channel.configureBlocking(false);

        AsyncCoordinator coordinator = newCoordinator();

        AsyncEndpoint endpoint = newEndpoint(channel, coordinator);
        coordinator.setEndpoint(endpoint);

        AsyncInterpreter interpreter = listener.connected(coordinator);
        coordinator.setInterpreter(interpreter);

        register(endpoint, coordinator);
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

    protected class AcceptWorker implements Runnable
    {
        public void run()
        {
            try
            {
                state = State.LISTEN;
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
                state = State.CLOSED;
                closeLock.lock();
                try
                {
                    closeCondition.signalAll();
                }
                finally
                {
                    closeLock.unlock();
                }
                logger.info("ServerConnector {}, acceptor loop exited", this);
            }
        }
    }

    private enum State
    {
        OPEN, LISTEN, CLOSED
    }
}
