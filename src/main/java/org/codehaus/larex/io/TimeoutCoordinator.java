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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @version $Revision$ $Date$
 */
public class TimeoutCoordinator extends StandardCoordinator
{
    private final ScheduledExecutorService scheduler;
    private final long readTimeout;
    private final long writeTimeout;
    private final Runnable readTimeoutAction = new ReadTimeoutAction();
    private final Runnable readTimeoutCommand = new ReadTimeoutCommand();
    private final AtomicReference<ReadState> readState = new AtomicReference<ReadState>(ReadState.WAIT);
    private final Runnable writeTimeoutCommand = new WriteTimeoutCommand();
    private volatile ScheduledFuture<?> readTimeoutTask;
    private volatile ScheduledFuture<?> writeTimeoutTask;

    public TimeoutCoordinator(Selector selector, Executor threadPool, ScheduledExecutorService scheduler, long readTimeout, long writeTimeout)
    {
        super(selector, threadPool);
        this.scheduler = scheduler;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
    }

    @Override
    public void onReadReady()
    {
        ScheduledFuture<?> task = readTimeoutTask;
        if (task != null)
            task.cancel(false);
        super.onReadReady();
    }

    @Override
    public void onWriteReady()
    {
        ScheduledFuture<?> task = writeTimeoutTask;
        if (task != null)
            task.cancel(false);
        super.onWriteReady();
    }

    @Override
    public void needsRead(boolean needsRead)
    {
        if (needsRead && readTimeout > 0)
            readTimeoutTask = scheduler.schedule(readTimeoutAction, readTimeout, TimeUnit.MILLISECONDS);
        super.needsRead(needsRead);
    }

    @Override
    public void needsWrite(boolean needsWrite)
    {
        if (needsWrite && writeTimeout > 0)
            writeTimeoutTask = scheduler.schedule(writeTimeoutCommand, writeTimeout, TimeUnit.MILLISECONDS);
        super.needsWrite(needsWrite);
    }

    @Override
    protected void read()
    {
        // Notify reads in any case
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

    private class ReadTimeoutAction implements Runnable
    {
        public void run()
        {
            // Delegating to thread pool guarantees that the scheduler is not delayed by slow user code
            getThreadPool().execute(readTimeoutCommand);
        }
    }

    private class ReadTimeoutCommand implements Runnable
    {
        public void run()
        {
            onReadTimeout();
        }
    }

    private class WriteTimeoutCommand implements Runnable
    {
        public void run()
        {
            onWriteTimeout();
        }
    }

    private enum ReadState
    {
        WAIT, READ, TIMEOUT
    }
}
