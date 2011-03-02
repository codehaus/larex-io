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
 * <p>Implementation of {@link Connection} that provides blocking read functionality
 * and inherits flush and close functionalities.</p>
 * <p>User code must implement {@link #openEvent()}, typically in the following way:</p>
 * <pre>
 * public void onOpen()
 * {
 *     ByteBuffer readBuffer = ByteBuffer.allocate(256);
 *     int read = read(readBuffer);
 *     // Do something with bytes just read
 *
 *     ByteBuffer writeBuffer = ByteBuffer.allocate(256);
 *     // Fill the writeBuffer with response data
 *     flush(writeBuffer);
 * }
 * </pre>
 *
 * @version $Revision$ $Date$
 */
public abstract class BlockingConnection extends FlushableConnection
{
    private ByteBuffer buffer;
    private ReadState readState = ReadState.WAIT;

    public BlockingConnection(Controller controller)
    {
        super(controller);
    }

    public abstract void onOpen();

    /**
     * <p>Overridden to implement the blocking read functionality.</p>
     *
     * @param buffer the buffer containing the bytes read
     */
    @Override
    public final void onRead(ByteBuffer buffer)
    {
        // TODO: fix this: if this.buffer is small we copy, notify,
        // TODO: but this method is called again because there is more data to read
        // TODO: need to wait until it's processed, or buffer it somewhere
        synchronized (this)
        {
            this.buffer.put(buffer);
            readState = ReadState.READ;
            notify();
        }
    }

    /**
     * <p>Overridden to implement the blocking read functionality.</p>
     */
    public final void onReadTimeout()
    {
        synchronized (this)
        {
            readState = ReadState.TIMEOUT;
            notify();
        }
    }

    /**
     * <p>Blocking reads bytes into the given buffer.</p>
     *
     * @param buffer the buffer to read bytes into
     * @return -1 if the remote end has been closed, or the number of bytes that has been read
     * @throws RuntimeSocketClosedException if this connection has been closed
     * @throws RuntimeSocketTimeoutException if the read timed out
     */
    protected int read(ByteBuffer buffer) throws RuntimeSocketClosedException, RuntimeSocketTimeoutException
    {
        int start = buffer.position();
        getController().setReadBufferSize(buffer.remaining());

        synchronized (this)
        {
            if (readState == ReadState.REMOTE_CLOSE)
                return -1;

            this.buffer = buffer;
            readState = ReadState.WAIT;
            getController().needsRead(true);
            while (readState == ReadState.WAIT)
            {
                try
                {
                    wait();
                }
                catch (InterruptedException x)
                {
                    close();
                    Thread.currentThread().interrupt();
                    throw new RuntimeSocketClosedException(new ClosedByInterruptException());
                }
            }

            if (buffer.position() == start)
            {
                if (readState == ReadState.TIMEOUT)
                    throw new RuntimeSocketTimeoutException();
                else if (readState == ReadState.CLOSE)
                    throw new RuntimeSocketClosedException();
                else if (readState == ReadState.REMOTE_CLOSE)
                    return -1;
            }
        }

        return buffer.position() - start;
    }

    /**
     * <p>Overridden to implement the blocking read functionality.</p>
     */
    void doOnRemoteClose()
    {
        super.doOnRemoteClose();
        synchronized (this)
        {
            readState = ReadState.REMOTE_CLOSE;
            notify();
        }
    }

    @Override
    void doOnClosing(StreamType type)
    {
        // TODO: fix this: needs to handle all stream types

        super.doOnClosing(type);
        if (type == StreamType.INPUT)
        {
            synchronized (this)
            {
                readState = ReadState.CLOSE;
                notify();
            }
        }
    }

    private enum ReadState
    {
        READ, WAIT, TIMEOUT, REMOTE_CLOSE, CLOSE
    }
}
