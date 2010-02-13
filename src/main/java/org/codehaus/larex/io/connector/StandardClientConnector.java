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
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.ThreadLocalByteBuffers;
import org.codehaus.larex.io.async.AsyncInterpreter;
import org.codehaus.larex.io.async.AsyncInterpreterFactory;
import org.codehaus.larex.io.async.AsyncSelector;
import org.codehaus.larex.io.async.ReadWriteAsyncSelector;

/**
 * @version $Revision$ $Date$
 */
public class StandardClientConnector
{
    private final Executor threadPool;
    private final ByteBuffers byteBuffers;
    private final AsyncSelector[] selectors;
    private final AtomicInteger selector = new AtomicInteger();

    public StandardClientConnector(Executor threadPool)
    {
        this(threadPool, 1);
    }

    public StandardClientConnector(Executor threadPool, int selectors)
    {
        this.threadPool = threadPool;
        this.byteBuffers = newByteBuffers();
        if (selectors < 1)
            throw new IllegalArgumentException("Invalid selectors count " + selectors + ": must be >= 1");
        this.selectors = new AsyncSelector[selectors];
        for (int i = 0; i < selectors; ++i)
            this.selectors[i] = newAsyncSelector();
    }

    protected ByteBuffers newByteBuffers()
    {
        return new ThreadLocalByteBuffers();
    }

    protected AsyncSelector newAsyncSelector()
    {
        return new ReadWriteAsyncSelector();
    }

    public <T extends AsyncInterpreter> Endpoint<T> newEndpoint(AsyncInterpreterFactory<T> interpreterFactory)
    {
        // Pick a selector
        int index = selector.incrementAndGet();
        index = Math.abs(index % selectors.length);
        AsyncSelector asyncSelector = selectors[index];

        try
        {
            SocketChannel channel = SocketChannel.open();
            return newEndpoint(channel, asyncSelector, interpreterFactory, threadPool, byteBuffers);
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    protected <T extends AsyncInterpreter> Endpoint<T> newEndpoint(SocketChannel channel, AsyncSelector asyncSelector, AsyncInterpreterFactory<T> interpreterFactory, Executor threadPool, ByteBuffers byteBuffers)
    {
        return new StandardEndpoint<T>(channel, asyncSelector, interpreterFactory, threadPool, byteBuffers);
    }
}
