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
import org.codehaus.larex.io.CachedByteBuffers;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Selector;
import org.codehaus.larex.io.TimeoutReadWriteSelector;

/**
 * @version $Revision$ $Date$
 */
public class ClientConnector
{
    private final Executor threadPool;
    private final AtomicInteger selectorIndex = new AtomicInteger();
    private volatile ByteBuffers byteBuffers;
    private volatile int selectorCount = 1;
    private volatile Selector[] selectors;

    public ClientConnector(Executor threadPool)
    {
        this.threadPool = threadPool;
    }

    public int getSelectorCount()
    {
        return selectorCount;
    }

    public void setSelectorCount(int selectorCount)
    {
        this.selectorCount = selectorCount;
    }

    public void open()
    {
        this.byteBuffers = newByteBuffers();

        this.selectors = new Selector[getSelectorCount()];
        for (int i = 0; i < selectors.length; ++i)
            this.selectors[i] = newSelector();
    }

    protected ByteBuffers newByteBuffers()
    {
        return new CachedByteBuffers();
    }

    protected Selector newSelector()
    {
        TimeoutReadWriteSelector selector = new TimeoutReadWriteSelector();
        selector.open();
        return selector;
    }

    protected Executor getThreadPool()
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

    public <C extends Connection> Endpoint<C> newEndpoint(ConnectionFactory<C> connectionFactory)
    {
        return new StandardEndpoint<C>(connectionFactory, chooseSelector(), getByteBuffers(), getThreadPool());
    }

    protected Selector chooseSelector()
    {
        int index = selectorIndex.incrementAndGet();
        Selector[] selectors = getSelectors();
        index = Math.abs(index % selectors.length);
        return selectors[index];
    }

    public void close()
    {
        for (Selector selector : selectors)
            selector.close();
    }

    public boolean join(long timeout) throws InterruptedException
    {
        boolean result = true;

        for (Selector selector : selectors)
            result &= selector.join(timeout);

        return result;
    }
}
