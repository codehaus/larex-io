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
 * @version $Revision$ $Date$
 */
public class TimeoutCoordinator extends StandardCoordinator
{
    private final Scheduler scheduler;
    private final long readTimeout;
    private final long writeTimeout;
    private final Scheduler.Task readTimeoutTask;
    private final Scheduler.Task writeTimeoutTask;
    private final AtomicReference<ReadState> readState = new AtomicReference<ReadState>(ReadState.WAIT);

    public TimeoutCoordinator(Selector selector, Executor threadPool, Scheduler scheduler, long readTimeout, long writeTimeout)
    {
        super(selector, threadPool);
        this.scheduler = scheduler;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;

        final Runnable onReadTimeout = new Runnable()
        {
            public void run()
            {
                onReadTimeout();
            }
        };
        this.readTimeoutTask = scheduler.newTask(new Runnable()
        {
            public void run()
            {
                getThreadPool().execute(onReadTimeout);
            }
        });

        final Runnable onWriteTimeout = new Runnable()
        {
            public void run()
            {
                onWriteTimeout();
            }
        };
        this.writeTimeoutTask = scheduler.newTask(new Runnable()
        {
            public void run()
            {
                getThreadPool().execute(onWriteTimeout);
            }
        });
    }

    @Override
    public void onReadReady()
    {
        scheduler.cancel(readTimeoutTask);
        super.onReadReady();
    }

    @Override
    public void onWriteReady()
    {
        scheduler.cancel(writeTimeoutTask);
        super.onWriteReady();
    }

    @Override
    public void needsRead(boolean needsRead)
    {
        if (needsRead && readTimeout > 0)
            scheduler.schedule(readTimeoutTask, readTimeout, TimeUnit.MILLISECONDS);
        super.needsRead(needsRead);
    }

    @Override
    public void needsWrite(boolean needsWrite)
    {
        if (needsWrite && writeTimeout > 0)
            scheduler.schedule(writeTimeoutTask, writeTimeout, TimeUnit.MILLISECONDS);
        super.needsWrite(needsWrite);
    }

    @Override
    protected void read()
    {
        // Notify reads in any case, even if we could not change the state
        boolean reading = readState.compareAndSet(ReadState.WAIT, ReadState.READ);
        try
        {
            super.read();
        }
        finally
        {
            if (reading)
                readState.compareAndSet(ReadState.READ, ReadState.WAIT);
        }
    }

    protected void onReadTimeout()
    {
        // Skip notification of read timeout in case a concurrent read happens
        if (readState.compareAndSet(ReadState.WAIT, ReadState.TIMEOUT))
        {
            try
            {
                logger.debug("Notifying read timeout");
                getConnection().onReadTimeout();
            }
            finally
            {
                readState.compareAndSet(ReadState.TIMEOUT, ReadState.WAIT);
            }
        }
    }

    protected void onWriteTimeout()
    {
        getConnection().onWriteTimeout();
    }

    private enum ReadState
    {
        WAIT, READ, TIMEOUT
    }
}
