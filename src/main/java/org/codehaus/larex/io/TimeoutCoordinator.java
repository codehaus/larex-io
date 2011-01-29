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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TimeoutCoordinator extends StandardCoordinator
{
    private final AtomicReference<State> readState = new AtomicReference<State>(State.WAIT);
    private final AtomicReference<State> writeState = new AtomicReference<State>(State.WAIT);
    private final long readTimeout;
    private final long writeTimeout;
    private volatile long readTime;
    private volatile long writeTime;

    public TimeoutCoordinator(Reactor reactor, ByteBuffers byteBuffers, long readTimeout, long writeTimeout)
    {
        super(reactor, byteBuffers);
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
    }

    @Override
    protected void processOnOpen()
    {
        super.processOnOpen();
        readTime = now();
    }

    @Override
    protected void readEnd(int read, boolean needsRead)
    {
        if (needsRead)
            readTime = now();
        super.readEnd(read, needsRead);
    }

    public void timeoutRead()
    {
        long readTime = this.readTime;
        if (readTime != 0L)
        {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(now() - readTime);
            if (elapsed > readTimeout)
                onReadTimeout();
        }
    }

    protected void onReadTimeout()
    {
        processOnReadTimeout();
    }

    @Override
    protected void writeBegin()
    {
        writeTime = 0L;
        super.writeBegin();
    }

    @Override
    protected void writeEnd(int written, boolean needsWrite)
    {
        if (needsWrite)
            writeTime = now();
        super.writeEnd(written, needsWrite);
    }

    public void timeoutWrite()
    {
        long writeTime = this.writeTime;
        if (writeTime != 0L)
        {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(now() - writeTime);
            if (elapsed > writeTimeout)
                onWriteTimeout();
        }
    }

    protected void onWriteTimeout()
    {
        processOnWriteTimeout();
    }

    @Override
    protected void processOnRead()
    {
        // Notify reads in any case, even if we could not change the state
        boolean reading = readState.compareAndSet(State.WAIT, State.ACTIVE);
        try
        {
            super.processOnRead();
        }
        finally
        {
            if (reading)
                readState.compareAndSet(State.ACTIVE, State.WAIT);
        }
    }

    protected void processOnReadTimeout()
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
    protected void processOnWrite()
    {
        // Notify writes in any case, even if we could not change the state
        boolean writing = writeState.compareAndSet(State.WAIT, State.ACTIVE);
        try
        {
            super.processOnWrite();
        }
        finally
        {
            if (writing)
                writeState.compareAndSet(State.ACTIVE, State.WAIT);
        }
    }

    protected void processOnWriteTimeout()
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
