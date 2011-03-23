/*
 * Copyright (c) 2011 the original author or authors
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A helper class that implements non blocking writes by buffering unwritten bytes to write
 * them at the first occasion, normally when the reactor signals that the underlying connection
 * is again ready to be written.</p>
 * <p>Read ready notification usually calls user code, that may write, and these writes may
 * be concurrent with write ready notifications, so this class synchronizes writes to avoid
 * they overlap.</p>
 */
public class NonBlockingWriter
{
    private static final Logger logger = LoggerFactory.getLogger(NonBlockingWriter.class);
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final Controller controller;
    /* Guarded by #this */
    private ByteBuffer buffer;

    public NonBlockingWriter(Controller controller)
    {
        this.controller = controller;
    }

    /**
     * <p>Method to be invoked when the underlying connection is ready to be written.</p>
     */
    public void writeReadyEvent()
    {
        synchronized (this)
        {
            boolean wrote = writeBuffer();
            if (wrote)
                releaseBuffer(buffer);
            else
                controller.needsWrite(true);
        }
    }

    /**
     * <p>Writes the currently buffered bytes.</p>
     *
     * @return whether all the buffered bytes have been written
     */
    protected boolean writeBuffer()
    {
        synchronized (this)
        {
            int written = write(controller, buffer);
            int remaining = buffer.remaining();
            if (logger.isDebugEnabled())
                logger.debug("{} wrote {} leftover bytes, {} bytes left to write", new Object[]{this, written, remaining});
            return remaining == 0;
        }
    }

    /**
     * <p>Writes the given {@code buffer} to the given {@code controller}.</p>
     *
     * @param controller the controller to write to
     * @param buffer     the buffer to write
     * @return the number of bytes written
     */
    protected int write(Controller controller, ByteBuffer buffer)
    {
        return controller.write(buffer);
    }

    /**
     * <p>Method to be invoked when the write timed out.</p>
     */
    public void writeTimeoutEvent()
    {
        synchronized (this)
        {
            if (buffer != null)
            {
                logger.info("{} write timeout, {} leftover bytes to write will be lost", this, buffer.remaining());
                releaseBuffer(buffer);
            }
        }
    }

    /**
     * <p>Method to be invoked when the connection is about to be closed.</p>
     */
    public void closingEvent()
    {
        synchronized (this)
        {
            if (buffer != null)
            {
                try
                {
                    // Attempt the last write
                    writeBuffer();
                    if (buffer.hasRemaining())
                        logger.info("{} closing, {} leftover bytes to write will be lost", this, buffer.remaining());
                }
                catch (RuntimeIOException x)
                {
                    logger.debug(this + " closing exception, " + buffer.remaining() + " leftover bytes to write will be lost", x);
                }
                finally
                {
                    releaseBuffer(buffer);
                }
            }
        }
    }

    /**
     * <p>Method to be invoked to write the given {@code bytes} buffer to the
     * underlying connection.</p>
     * <p>The given {@code bytes} buffer is always fully consumed, either because
     * it has been written or because it has been buffered.</p>
     * <p>This method returns the number of bytes actually written from
     * the given {@code bytes} buffer, so if the given {@code bytes} buffer is
     * totally buffered, this method returns zero.
     *
     * @param bytes the buffer to write
     * @return the number of bytes written
     */
    public int write(ByteBuffer bytes)
    {
        boolean debug = logger.isDebugEnabled();

        synchronized (this)
        {
            if (buffer != null)
            {
                boolean wrote = writeBuffer();
                if (wrote)
                {
                    releaseBuffer(buffer);
                }
                else
                {
                    ByteBuffer temporary = acquireBuffer(buffer.remaining() + bytes.remaining());
                    temporary.put(buffer);
                    temporary.put(bytes);
                    temporary.flip();
                    releaseBuffer(buffer);
                    this.buffer = temporary;
                    controller.needsWrite(true);
                    return 0;
                }
            }

            if (debug)
                logger.debug("{} writing {} bytes", this, bytes.remaining());
            int written = write(controller, bytes);
            int remaining = bytes.remaining();
            if (debug)
                logger.debug("{} wrote {} bytes, {} bytes left to write", new Object[]{this, written, remaining});
            if (remaining > 0)
            {
                ByteBuffer temporary = acquireBuffer(bytes.remaining());
                temporary.put(bytes);
                temporary.flip();
                this.buffer = temporary;
                controller.needsWrite(true);
            }

            return written;
        }
    }

    /**
     * @return a read-only ByteBuffer of the buffered bytes
     *         or an empty ByteBuffer if there are no buffered bytes
     */
    protected ByteBuffer getBuffer()
    {
        synchronized (this)
        {
            if (buffer == null)
                return EMPTY_BUFFER;
            return buffer.asReadOnlyBuffer();
        }
    }

    /**
     * <p>Allocates and return a buffer of the given capacity, used to store bytes that
     * cannot be written immediately.</p>
     *
     * @param size the capacity of the buffer
     * @return a buffer to store unwritten bytes
     * @see #releaseBuffer(ByteBuffer)
     */
    protected ByteBuffer acquireBuffer(int size)
    {
        return ByteBuffer.allocateDirect(size);
    }

    /**
     * <p>Releases the given buffer, acquired via {@link #acquireBuffer(int)}.</p>
     *
     * @param buffer the buffer to release
     * @see #acquireBuffer(int)
     */
    protected void releaseBuffer(ByteBuffer buffer)
    {
        this.buffer = null;
    }
}
