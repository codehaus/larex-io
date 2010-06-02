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

package org.codehaus.larex.io;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TODO: adding a task for timeouts is expensive for 20k connections
 * If all connections gets created at same time, there are 20k tasks running at the same time
 * Coalesce ? Or have one static task that runs over all coordinators ?
 *
 * @version $Revision$ $Date$
 */
public class TimeoutCoordinator extends StandardCoordinator
{
    private final AtomicReference<State> readState = new AtomicReference<State>(State.WAIT);
    private final AtomicReference<State> writeState = new AtomicReference<State>(State.WAIT);
    private final long readTimeout;
    private final long writeTimeout;
    private volatile long readTime;
    private volatile long writeTime;

    public TimeoutCoordinator(Selector selector, ByteBuffers byteBuffers, Executor threadPool, long readTimeout, long writeTimeout)
    {
        super(selector, byteBuffers, threadPool);
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
    }

    public void timeoutRead()
    {
        long readTime = this.readTime;
        if (readTime != 0L)
        {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(now() - readTime);
            if (elapsed > readTimeout)
            {
                getThreadPool().execute(new Runnable()
                {
                    public void run()
                    {
                        onReadTimeout();
                    }
                });
            }
        }
    }

    public void timeoutWrite()
    {
        long writeTime = this.writeTime;
        if (writeTime != 0L)
        {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(now() - writeTime);
            if (elapsed > writeTimeout)
            {
                getThreadPool().execute(new Runnable()
                {
                    public void run()
                    {
                        onWriteTimeout();
                    }
                });
            }
        }
    }

    @Override
    public void onReadReady()
    {
        readTime = 0L;
        super.onReadReady();
    }

    @Override
    public void needsRead(boolean needsRead)
    {
        if (needsRead)
            readTime = now();
        super.needsRead(needsRead);
    }

    @Override
    public void onWriteReady()
    {
        writeTime = 0L;
        super.onWriteReady();
    }

    @Override
    public void needsWrite(boolean needsWrite)
    {
        if (needsWrite)
            writeTime = now();
        super.needsWrite(needsWrite);
    }

    @Override
    protected void onReadAction()
    {
        // Notify reads in any case, even if we could not change the state
        boolean reading = readState.compareAndSet(State.WAIT, State.ACTIVE);
        try
        {
            super.onReadAction();
        }
        finally
        {
            if (reading)
                readState.compareAndSet(State.ACTIVE, State.WAIT);
        }
    }

    protected void onReadTimeout()
    {
        // Skip notification of read timeout in case a concurrent read event happens
        if (readState.compareAndSet(State.WAIT, State.TIMEOUT))
        {
            try
            {
                getInterceptor().onReadTimeout();
            }
            finally
            {
                readState.compareAndSet(State.TIMEOUT, State.WAIT);
            }
        }
    }

    @Override
    protected void onWriteAction()
    {
        // Notify writes in any case, even if we could not change the state
        boolean writing = writeState.compareAndSet(State.WAIT, State.ACTIVE);
        try
        {
            super.onWriteAction();
        }
        finally
        {
            if (writing)
                writeState.compareAndSet(State.ACTIVE, State.WAIT);
        }
    }

    protected void onWriteTimeout()
    {
        // Skip notification of write timeout in case a concurrent write event happens
        if (writeState.compareAndSet(State.WAIT, State.TIMEOUT))
        {
            try
            {
                getInterceptor().onWriteTimeout();
            }
            finally
            {
                writeState.compareAndSet(State.TIMEOUT, State.WAIT);
            }
        }
    }

    private long now()
    {
        return System.nanoTime();
    }

    private enum State
    {
        WAIT, ACTIVE, TIMEOUT
    }
}
