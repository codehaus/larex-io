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

/**
 * <p>Partial implementation of {@link Connection}, that provides write functionalities
 * and inherits close functionalities.</p>
 * <p>Writes can be non-blocking (via {@link #flush(ByteBuffer)}), or blocking (via
 * {@link #write(ByteBuffer)}.</p>
 *
 * @version $Revision$ $Date$
 */
public abstract class WritableConnection extends ClosableConnection
{
    private final Object writeLock = new Object();
    private WriteState writeState = WriteState.WRITE;

    protected WritableConnection(Coordinator coordinator)
    {
        super(coordinator);
    }

    /**
     * <p>Copies the given source buffer into a new buffer.</p>
     *
     * @param source the buffer to copy
     * @return the copied buffer
     */
    public ByteBuffer copy(ByteBuffer source)
    {
        ByteBuffer result = ByteBuffer.allocate(source.remaining());
        result.put(source);
        result.flip();
        return result;
    }

    /**
     * <p>Overridden to implement the blocking write functionality.</p>
     * <p>Override {@link #onWriteHook()} instead.</p>
     */
    public final void onWrite()
    {
        synchronized (writeLock)
        {
            writeState = WriteState.WRITE;
            writeLock.notify();
        }
        onWriteHook();
    }

    /**
     * <p>Callback invoked by {@link #onWrite()}.</p>
     */
    protected void onWriteHook()
    {
    }

    /**
     * <p>Overridden to implement the blocking write functionality.</p>
     * <p>Override {@link #onWriteTimeoutHook()} instead.</p>
     */
    public final void onWriteTimeout()
    {
        synchronized (writeLock)
        {
            writeState = WriteState.TIMEOUT;
            writeLock.notify();
        }
        onWriteTimeoutHook();
    }

    /**
     * <p>Callback invoked by {@link #onWriteTimeout()}.</p>
     */
    protected void onWriteTimeoutHook()
    {
    }

    /**
     * <p>Blocking-writes the bytes contained in the given buffer.</p>
     * <p>This call is blocking and will only return when all the bytes have been written,
     * the write timeout expires, or the connection is closed.</p>
     *
     * @param buffer the buffer to write
     * @throws RuntimeSocketTimeoutException if the write timeout expires
     * @throws RuntimeSocketClosedException  if the connection is closed
     */
    public final void write(ByteBuffer buffer) throws RuntimeSocketTimeoutException, RuntimeSocketClosedException
    {
        while (buffer.hasRemaining())
        {
            int written = flush(buffer);
            if (debug)
                logger.debug("{} wrote {} bytes", this, written);

            if (buffer.hasRemaining())
            {
                // We could not write everything, suspend the writer thread until we are write ready
                synchronized (writeLock)
                {
                    if (writeState == WriteState.CLOSE)
                        throw new RuntimeSocketClosedException();

                    writeState = WriteState.WAIT;
                    // We must issue the needsWrite() below within the sync block, otherwise
                    // another thread can issue a notify that no one is ready to listen and
                    // this thread will wait forever for a notify that already happened.
                    getCoordinator().needsWrite(true);

                    while (writeState == WriteState.WAIT)
                    {
                        try
                        {
                            if (debug)
                                logger.debug("Writer thread {} suspended on partial write, {} bytes remaining", Thread.currentThread(), buffer.remaining());
                            writeLock.wait();
                            if (debug)
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

                    if (writeState == WriteState.TIMEOUT)
                        throw new RuntimeSocketTimeoutException();
                    else if (writeState == WriteState.CLOSE)
                        throw new RuntimeSocketClosedException();
                }
            }
        }
    }

    /**
     * <p>Non-blocking write the bytes contained in the given buffer.</p>
     * @param buffer the buffer to flush
     * @return the bytes written
     */
    public int flush(ByteBuffer buffer)
    {
        return getCoordinator().write(buffer);
    }

    @Override
    void doClose()
    {
        synchronized (writeLock)
        {
            writeState = WriteState.CLOSE;
            writeLock.notify();
        }
        super.doClose();
    }

    private enum WriteState
    {
        WRITE, WAIT, TIMEOUT, CLOSE
    }
}
