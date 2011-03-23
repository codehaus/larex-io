/*
 * Copyright (c) 2010 the original author or authors
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
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.CachedByteBuffers;
import org.codehaus.larex.io.Channel;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.Coordinator;
import org.codehaus.larex.io.DispatchCoordinator;
import org.codehaus.larex.io.Reactor;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.RuntimeSocketConnectException;
import org.codehaus.larex.io.RuntimeSocketTimeoutException;
import org.codehaus.larex.io.StandardChannel;
import org.codehaus.larex.io.TimeoutReadWriteReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ClientConnector
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Executor threadPool;
    private final AtomicInteger reactorIndex = new AtomicInteger();
    private volatile ByteBuffers byteBuffers;
    private volatile int reactorCount = 1;
    private volatile Reactor[] reactors;

    public ClientConnector(Executor threadPool)
    {
        this.threadPool = threadPool;
    }

    public int getReactorCount()
    {
        return reactorCount;
    }

    public void setReactorCount(int reactorCount)
    {
        this.reactorCount = reactorCount;
    }

    public void open()
    {
        this.byteBuffers = newByteBuffers();

        this.reactors = new Reactor[getReactorCount()];
        for (int i = 0; i < reactors.length; ++i)
            this.reactors[i] = newReactor();
    }

    protected ByteBuffers newByteBuffers()
    {
        return new CachedByteBuffers();
    }

    protected Reactor newReactor()
    {
        TimeoutReadWriteReactor reactor = new TimeoutReadWriteReactor();
        reactor.open();
        return reactor;
    }

    public Executor getThreadPool()
    {
        return threadPool;
    }

    public ByteBuffers getByteBuffers()
    {
        return byteBuffers;
    }

    protected Reactor[] getReactors()
    {
        return reactors;
    }

    public <C extends Connection> ConnectionBuilder<C, ?> buildConnection(ConnectionFactory<C> connectionFactory)
    {
        return new Builder<C>(connectionFactory, chooseReactor());
    }

    protected Reactor chooseReactor()
    {
        int index = reactorIndex.incrementAndGet();
        Reactor[] reactors = getReactors();
        index = Math.abs(index % reactors.length);
        return reactors[index];
    }

    public void close()
    {
        logger.debug("{} closing", this);
        for (Reactor reactor : reactors)
            reactor.close();
        logger.debug("{} closed", this);
    }

    public boolean join(long timeout) throws InterruptedException
    {
        boolean result = true;

        for (Reactor reactor : reactors)
            result &= reactor.join(timeout);

        return result;
    }

    public abstract class ConnectionBuilder<C extends Connection, B extends ConnectionBuilder<C, B>>
    {
        private final SocketChannel channel;
        private final ConnectionFactory<C> connectionFactory;
        private final Reactor reactor;
        private InetSocketAddress bindAddress;
        private boolean tcpNoDelay = true;
        private boolean reuseAddress = true;
        private long connectTimeout = 10000;
        private long readTimeout = 120000;
        private long writeTimeout = 30000;

        public ConnectionBuilder(ConnectionFactory<C> connectionFactory, Reactor reactor)
        {
            try
            {
                this.channel = SocketChannel.open();
                this.connectionFactory = connectionFactory;
                this.reactor = reactor;
            }
            catch (IOException x)
            {
                throw new RuntimeIOException(x);
            }
        }

        protected abstract B self();

        public SocketChannel socketChannel()
        {
            return channel;
        }

        protected Reactor reactor()
        {
            return reactor;
        }

        public InetSocketAddress bindAddress()
        {
            return bindAddress;
        }

        public B bindAddress(InetSocketAddress bindAddress)
        {
            this.bindAddress = bindAddress;
            return self();
        }

        public boolean tcpNoDelay()
        {
            return tcpNoDelay;
        }

        public B tcpNoDelay(boolean tcpNoDelay)
        {
            this.tcpNoDelay = tcpNoDelay;
            return self();
        }

        public boolean reuseAddress()
        {
            return reuseAddress;
        }

        public B reuseAddress(boolean reuseAddress)
        {
            this.reuseAddress = reuseAddress;
            return self();
        }

        public long connectTimeout()
        {
            return connectTimeout;
        }

        public B connectTimeout(long connectTimeout)
        {
            this.connectTimeout = connectTimeout;
            return self();
        }

        public long readTimeout()
        {
            return readTimeout;
        }

        public B readTimeout(long readTimeout)
        {
            this.readTimeout = readTimeout;
            return self();
        }

        public long writeTimeout()
        {
            return writeTimeout;
        }

        public B writeTimeout(long writeTimeout)
        {
            this.writeTimeout = writeTimeout;
            return self();
        }

        public C connect(InetSocketAddress address)
        {
            try
            {
                Socket socket = channel.socket();

                socket.setTcpNoDelay(tcpNoDelay());
                socket.setReuseAddress(reuseAddress());

                InetSocketAddress bindAddress = bindAddress();
                if (bindAddress != null)
                {
                    socket.bind(bindAddress);
                    logger.debug("{} bound to {}", this, bindAddress);
                }

                long connectTimeout = connectTimeout();
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
            socketChannel().configureBlocking(false);

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
            return new DispatchCoordinator(reactor(), getByteBuffers(), getThreadPool(), readTimeout(), writeTimeout());
        }

        protected Channel newChannel(Controller controller)
        {
            return new StandardChannel(reactor(), socketChannel(), controller);
        }

        protected void register(Channel channel, Reactor.Listener listener)
        {
            reactor().register(channel, listener);
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

    private class Builder<C extends Connection> extends ConnectionBuilder<C, Builder<C>>
    {
        private Builder(ConnectionFactory<C> factory, Reactor reactor)
        {
            super(factory, reactor);
        }

        @Override
        protected Builder<C> self()
        {
            return this;
        }
    }
}
