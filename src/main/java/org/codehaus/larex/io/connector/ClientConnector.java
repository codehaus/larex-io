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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.CachedByteBuffers;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Reactor;
import org.codehaus.larex.io.TimeoutReadWriteReactor;

/**
 *
 */
public class ClientConnector
{
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

    public <C extends Connection> Endpoint<C> newEndpoint(ConnectionFactory<C> connectionFactory)
    {
        return new StandardEndpoint<C>(connectionFactory, chooseReactor(), getByteBuffers(), getThreadPool());
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
        for (Reactor reactor : reactors)
            reactor.close();
    }

    public boolean join(long timeout) throws InterruptedException
    {
        boolean result = true;

        for (Reactor reactor : reactors)
            result &= reactor.join(timeout);

        return result;
    }
}
