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

/**
 * <p>Partial implementation of {@link Connection}, that provides flush functionalities
 * and inherits close functionalities.</p>
 * <p>Writes can be non-blocking (via {@link #write(ByteBuffer)}), or blocking (via
 * {@link #flush(ByteBuffer)}.</p>
 *
 * @version $Revision$ $Date$
 */
public abstract class FlushableConnection extends ClosableConnection
{
    private final Flusher flusher;

    protected FlushableConnection(Coordinator coordinator)
    {
        super(coordinator);
        this.flusher = new ConnectionFlusher(coordinator);
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

    @Override
    void doOnWrite()
    {
        super.doOnWrite();
        flusher.writeReadyEvent();
    }

    @Override
    void doOnWriteTimeout()
    {
        super.doOnWriteTimeout();
        flusher.writeTimeoutEvent();
    }

    @Override
    void doClose(StreamType type)
    {
        super.doClose(type);
        if (type == StreamType.OUTPUT)
            flusher.closeEvent();
    }

    @Override
    void doClose()
    {
        super.doOnClosed();
        flusher.closeEvent();
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
    public final void flush(ByteBuffer buffer) throws RuntimeSocketTimeoutException, RuntimeSocketClosedException
    {
        flusher.flush(buffer);
    }

    /**
     * <p>Non-blocking write the bytes contained in the given buffer.</p>
     *
     * @param buffer the buffer to flush
     * @return the bytes written
     */
    public final int write(ByteBuffer buffer)
    {
        return getCoordinator().write(buffer);
    }

    private class ConnectionFlusher extends Flusher
    {
        private ConnectionFlusher(Coordinator coordinator)
        {
            super(coordinator);
        }

        @Override
        protected int write(ByteBuffer buffer)
        {
            return FlushableConnection.this.write(buffer);
        }

        @Override
        protected void close()
        {
            FlushableConnection.this.close();
        }
    }
}
