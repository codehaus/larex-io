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

package org.codehaus.larex.io.async;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;

import org.codehaus.larex.io.RuntimeSocketClosedException;
import org.codehaus.larex.io.RuntimeSocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision$ $Date$
 */
public abstract class AbstractConnection implements Connection
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Coordinator coordinator;
    private State state = State.WRITE;

    public AbstractConnection(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    protected Coordinator getCoordinator()
    {
        return coordinator;
    }

    protected ByteBuffer copy(ByteBuffer source)
    {
        ByteBuffer result = ByteBuffer.allocate(source.remaining());
        result.put(source);
        result.flip();
        return result;
    }

    public void onWrite()
    {
        synchronized (this)
        {
            state = State.WRITE;
            notify();
        }
    }

    public void onWriteTimeout()
    {
        synchronized (this)
        {
            state = State.TIMEOUT;
            notify();
        }
    }

    protected void write(ByteBuffer buffer)
    {
        while (buffer.hasRemaining())
        {
            int written = coordinator.write(buffer);
            logger.debug("{} wrote {} bytes", this, written);

            if (buffer.hasRemaining())
            {
                // We could not write everything, suspend the writer thread until we are write ready
                synchronized (this)
                {
                    if (state == State.CLOSE)
                        throw new RuntimeSocketClosedException();

                    state = State.WAIT;
                    // We must issue the needsWrite() below within the sync block, otherwise
                    // another thread can issue a notify that no one is ready to listen and
                    // this thread will wait forever for a notify that already happened.
                    coordinator.needsWrite(true);

                    while (state == State.WAIT)
                    {
                        try
                        {
                            logger.debug("Writer thread {} suspended on partial write, {} bytes remaining", Thread.currentThread(), buffer.remaining());
                            wait();
                            logger.debug("Writer thread {} resumed, {} bytes remaining", Thread.currentThread(), buffer.remaining());
                        }
                        catch (InterruptedException x)
                        {
                            logger.debug("Writer thread {} interrupted on pending write", Thread.currentThread());
                            close();
                            Thread.currentThread().interrupt();
                            throw new RuntimeSocketClosedException(new ClosedByInterruptException());
                        }
                    }

                    if (state == State.TIMEOUT)
                        throw new RuntimeSocketTimeoutException();
                    else if (state == State.CLOSE)
                        throw new RuntimeSocketClosedException();
                }
            }
        }
    }

    protected void close()
    {
        synchronized (this)
        {
            state = State.CLOSE;
            notify();
        }
        coordinator.close();
    }

    private enum State
    {
        WRITE, WAIT, TIMEOUT, CLOSE
    }
}
