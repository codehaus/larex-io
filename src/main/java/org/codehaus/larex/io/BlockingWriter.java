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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class BlockingWriter
{
    private static final Logger logger = LoggerFactory.getLogger(BlockingWriter.class);

    private final Controller controller;
    /* Guarded by #this */
    private FlushState writeState = FlushState.WRITE;

    protected BlockingWriter(Controller controller)
    {
        this.controller = controller;
    }

    protected int write(Controller controller, ByteBuffer buffer)
    {
        return controller.write(buffer);
    }

    public void writeReadyEvent()
    {
        synchronized (this)
        {
            writeState = FlushState.WRITE;
            notify();
        }
    }

    public void writeTimeoutEvent()
    {
        synchronized (this)
        {
            writeState = FlushState.TIMEOUT;
            notify();
        }
    }

    public void closeEvent()
    {
        synchronized (this)
        {
            writeState = FlushState.CLOSE;
            notify();
        }
    }

    public void write(ByteBuffer buffer)
    {
        final boolean debug = logger.isDebugEnabled();

        while (buffer.hasRemaining())
        {
            int written = write(controller, buffer);
            if (debug)
                logger.debug("{} written {} bytes", this, written);

            if (buffer.hasRemaining())
            {
                // We could not write everything, suspend the writer thread until we are write ready
                synchronized (this)
                {
                    if (writeState == FlushState.CLOSE)
                        throw new RuntimeSocketClosedException();

                    writeState = FlushState.WAIT;

                    // We must issue the needsWrite() below within the sync block, otherwise
                    // another thread can issue a notify that no one is ready to listen and
                    // this thread will wait forever for a notify that already happened.
                    controller.needsWrite(true);

                    while (writeState == FlushState.WAIT)
                    {
                        try
                        {
                            if (debug)
                                logger.debug("Flusher thread {} suspended on partial write, {} bytes remaining", Thread.currentThread(), buffer.remaining());
                            wait();
                            if (debug)
                                logger.debug("Flusher thread {} resumed, {} bytes remaining", Thread.currentThread(), buffer.remaining());
                        }
                        catch (InterruptedException x)
                        {
                            if (debug)
                                logger.debug("Flusher thread {} interrupted on pending write", Thread.currentThread());
                            // Apply below same semantic of ClosedByInterruptException
                            controller.close(StreamType.INPUT_OUTPUT);
                            Thread.currentThread().interrupt();
                            throw new RuntimeSocketClosedException(new ClosedByInterruptException());
                        }
                    }

                    if (writeState == FlushState.TIMEOUT)
                        throw new RuntimeSocketTimeoutException(); // TODO: must close ?
                    else if (writeState == FlushState.CLOSE)
                        throw new RuntimeSocketClosedException();
                }
            }
        }
    }

    private enum FlushState
    {
        WRITE, WAIT, TIMEOUT, CLOSE
    }
}
