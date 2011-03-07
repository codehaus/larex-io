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

/**
 * <p>Implementation of {@link Connection} that provides blocking read functionality,
 * blocking write functionalities and inherits close functionalities.</p>
 * <p>User code must implement {@link #onOpen()}, typically in the following way:</p>
 * <pre>
 * public void onOpen()
 * {
 *     ByteBuffer readBuffer = ByteBuffer.allocate(256);
 *     int read = read(readBuffer);
 *     // Do something with bytes just read
 *
 *     ByteBuffer writeBuffer = ByteBuffer.allocate(256);
 *     // Fill the writeBuffer with response data
 *     write(writeBuffer);
 * }
 * </pre>
 */
public class BlockingConnection extends ClosableConnection
{
    private final Lock lock = new ReentrantLock();
    private final Condition reading = lock.newCondition();
    private final Condition buffering = lock.newCondition();
    private final int capacity;
    /* Guarded by #lock */
    private ByteBuffer buffer;
    /* Guarded by #lock */
    private ReadState readState = ReadState.WAIT;

    public BlockingConnection(Controller controller)
    {
        this(controller, 1024);
    }

    public BlockingConnection(Controller controller, int capacity)
    {
        super(controller);
        this.capacity = capacity;
    }

    /**
     * <p>Overridden to implement the blocking read functionality.</p>
     *
     * @param buffer the buffer containing the bytes read
     */
    @Override
    protected final void onRead(ByteBuffer buffer)
    {
        lock.lock();
        try
        {
            if (this.buffer == null)
                this.buffer = ByteBuffer.allocate(capacity);

            while (this.buffer.remaining() < buffer.remaining())
            {
                buffering.await();
                // TODO: does it solve the problem ?
                // TODO: We make this thread wait, the key is zero, but the reactor will notify us again that we need to read more ?
                // TODO: Answer is YES

                // TODO: at this point I wonder if it's not better to return something from onRead() to signal
                // TODO: "proceed reading" or "do not set read interest".
                // TODO: here we would just return false, and no hassles with threads in wait to be notified
                // TODO: we would just call needsRead(true) if we need to read more
            }

            this.buffer.put(buffer);
        }
        finally
        {
            lock.unlock();
        }

        // TODO: fix this: if this.buffer is small we copy, notify,
        // TODO: but this method is called again because there is more data to read
        // TODO: need to wait until it's processed, or buffer it somewhere
        // TODO: instead of notifying here we should notify from onReadEnd()
        synchronized (this)
        {
            this.buffer.put(buffer);
            readState = ReadState.READ;
            notify();
        }
    }

    @Override
    protected final boolean onReadEnd()
    {
        lock.lock();
        try
        {
            reading.signal();
            return true;
        }
        finally
        {
            lock.unlock();
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
        getController().setReadBufferSize(buffer.remaining()); // TODO: no need for this since we do multiple reads

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

    public int available()
    {
        return 0;
    }

    private enum ReadState
    {
        READ, WAIT, TIMEOUT, REMOTE_CLOSE, CLOSE
    }
}
