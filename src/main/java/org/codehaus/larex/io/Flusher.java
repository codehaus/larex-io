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

import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision$ $Date$
 */
public abstract class Flusher
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Lock flushLock = new ReentrantLock();
    private final Condition flushCondition = flushLock.newCondition();
    private final Controller coordinator;
    /**
     * Field updates are guarded by {@link #flushLock}
     */
    private FlushState flushState = FlushState.WRITE;

    protected Flusher(Controller controller)
    {
        this.coordinator = controller;
    }

    protected abstract int write(ByteBuffer buffer);

    protected abstract void close();

    public void writeReadyEvent()
    {
        flushLock.lock();
        try
        {
            flushState = FlushState.WRITE;
            flushCondition.signal();
        }
        finally
        {
            flushLock.unlock();
        }
    }

    public void writeTimeoutEvent()
    {
        flushLock.lock();
        try
        {
            flushState = FlushState.TIMEOUT;
            flushCondition.signal();
        }
        finally
        {
            flushLock.unlock();
        }
    }

    public void closeEvent()
    {
        flushLock.lock();
        try
        {
            flushState = FlushState.CLOSE;
            flushCondition.signal();
        }
        finally
        {
            flushLock.unlock();
        }
    }

    public void needsWrite()
    {
        coordinator.needsWrite(true);
    }

    public void flush(ByteBuffer buffer)
    {
        while (buffer.hasRemaining())
        {
            int written = write(buffer);
            if (logger.isDebugEnabled())
                logger.debug("{} flushed {} bytes", this, written);

            if (buffer.hasRemaining())
            {
                // We could not write everything, suspend the writer thread until we are write ready
                flushLock.lock();
                try
                {
                    if (flushState == FlushState.CLOSE)
                        throw new RuntimeSocketClosedException();

                    flushState = FlushState.WAIT;
                    // We must issue the needsWrite() below within the sync block, otherwise
                    // another thread can issue a notify that no one is ready to listen and
                    // this thread will wait forever for a notify that already happened.
                    needsWrite();

                    while (flushState == FlushState.WAIT)
                    {
                        try
                        {
                            if (logger.isDebugEnabled())
                                logger.debug("Flusher thread {} suspended on partial write, {} bytes remaining", Thread.currentThread(), buffer.remaining());
                            flushCondition.await();
                            if (logger.isDebugEnabled())
                                logger.debug("Flusher thread {} resumed, {} bytes remaining", Thread.currentThread(), buffer.remaining());
                        }
                        catch (InterruptedException x)
                        {
                            logger.debug("Flusher thread {} interrupted on pending write", Thread.currentThread());
                            close();
                            Thread.currentThread().interrupt();
                            throw new RuntimeSocketClosedException(new ClosedByInterruptException());
                        }
                    }

                    if (flushState == FlushState.TIMEOUT)
                        throw new RuntimeSocketTimeoutException();
                    else if (flushState == FlushState.CLOSE)
                        throw new RuntimeSocketClosedException();
                }
                finally
                {
                    flushLock.unlock();
                }
            }
        }
    }

    private enum FlushState
    {
        WRITE, WAIT, TIMEOUT, CLOSE
    }
}
