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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.ReadWriteSelector;
import org.codehaus.larex.io.Scheduler;
import org.codehaus.larex.io.Selector;
import org.codehaus.larex.io.ThreadLocalByteBuffers;

/**
 * @version $Revision$ $Date$
 */
public class ClientConnector
{
    private final Executor threadPool;
    private final Scheduler scheduler;
    private final ByteBuffers byteBuffers;
    private final Selector[] selectors;
    private final AtomicInteger selector = new AtomicInteger();

    public ClientConnector(Executor threadPool, Scheduler scheduler)
    {
        this(threadPool, scheduler, 1);
    }

    public ClientConnector(Executor threadPool, Scheduler scheduler, int selectors)
    {
        this.threadPool = threadPool;
        this.scheduler = scheduler;
        this.byteBuffers = newByteBuffers();
        if (selectors < 1)
            throw new IllegalArgumentException("Invalid selectors count " + selectors + ": must be >= 1");
        this.selectors = new Selector[selectors];
        for (int i = 0; i < selectors; ++i)
            this.selectors[i] = newSelector();
    }

    protected ByteBuffers newByteBuffers()
    {
        return new ThreadLocalByteBuffers();
    }

    protected Selector newSelector()
    {
        return new ReadWriteSelector();
    }

    protected Executor getThreadPool()
    {
        return threadPool;
    }

    protected Scheduler getScheduler()
    {
        return scheduler;
    }

    protected ByteBuffers getByteBuffers()
    {
        return byteBuffers;
    }

    protected Selector[] getSelectors()
    {
        return selectors;
    }

    public <C extends Connection> Endpoint<C> newEndpoint(ConnectionFactory<C> connectionFactory)
    {
        return new StandardEndpoint<C>(connectionFactory, chooseSelector(), getByteBuffers(), getThreadPool(), getScheduler());
    }

    protected Selector chooseSelector()
    {
        int index = selector.incrementAndGet();
        Selector[] selectors = getSelectors();
        index = Math.abs(index % selectors.length);
        return selectors[index];
    }

    public void close()
    {
        for (Selector selector : selectors)
            selector.close();
    }

    public void join(long timeout) throws InterruptedException
    {
        for (Selector selector : selectors)
            selector.join(timeout);
    }
}
