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
    private static final Logger logger = LoggerFactory.getLogger(BlockingConnection.class);
    /* Guarded by #this */
    private ByteBuffer store;
    /* Guarded by #this */
    private ReadState readState = ReadState.WAIT;

    public BlockingConnection(Controller controller)
    {
        super(controller);
    }

    /**
     * <p>Overridden to implement the blocking read functionality.</p>
     *
     * @param buffer the buffer containing the bytes to read
     */
    @Override
    protected final boolean onRead(ByteBuffer buffer)
    {
        synchronized (this)
        {
            if (store == null)
            {
                store = ByteBuffer.allocate(buffer.remaining());
                store.put(buffer);
                store.flip();
                readState = ReadState.READ;
                notify();
                logger.debug("read {} bytes available, notified reader thread", store.remaining());
                return false;
            }
            else
            {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * <p>Blocking reads bytes into the given buffer.</p>
     *
     * @param buffer the buffer to read bytes into
     * @return -1 if the remote end has been closed, or the number of bytes that has been read
     * @throws RuntimeSocketClosedException  if this connection has been closed
     * @throws RuntimeSocketTimeoutException if the read timed out
     */
    protected int read(ByteBuffer buffer) throws RuntimeSocketClosedException, RuntimeSocketTimeoutException
    {
        synchronized (this)
        {
            if (store != null)
            {
                int bytes = store.remaining();
                int space = buffer.remaining();
                if (bytes <= space)
                {
                    buffer.put(store);
                    store = null;
                    getController().needsRead(true);
                    logger.debug("read {} bytes", bytes);
                    return bytes;
                }
                else
                {
                    int limit = store.limit();
                    store.limit(store.position() + space);
                    buffer.put(store);
                    store.limit(limit);
                    logger.debug("read {} bytes, {} bytes available", space, store.remaining());
                    return space;
                }
            }

            if (readState == ReadState.TIMEOUT)
                throw new RuntimeSocketTimeoutException();
            else if (readState == ReadState.CLOSE)
                throw new RuntimeSocketClosedException();
            else if (readState == ReadState.REMOTE_CLOSE)
                return -1;

            readState = ReadState.WAIT;
            getController().needsRead(true);
            while (readState == ReadState.WAIT)
            {
                try
                {
                    logger.debug("read waiting for bytes");
                    wait();
                }
                catch (InterruptedException x)
                {
                    logger.debug("read waiting interrupted");
                    // Apply below same semantic of ClosedByInterruptException
                    close();
                    Thread.currentThread().interrupt();
                    throw new RuntimeSocketClosedException(new ClosedByInterruptException());
                }
            }
            logger.debug("read notified, state {}", readState);
            return read(buffer);
        }
    }

    void postReadTimeout()
    {
        synchronized (this)
        {
            readState = ReadState.TIMEOUT;
            notify();
            logger.debug("read timeout, notified reader thread");
        }
    }

    /**
     * <p>Overridden to implement the blocking read functionality.</p>
     */
    void postRemoteClose()
    {
        super.postRemoteClose();
        synchronized (this)
        {
            readState = ReadState.REMOTE_CLOSE;
            notify();
            logger.debug("remote close, notified reader thread");
        }
    }

    @Override
    void postClosing(StreamType type)
    {
        super.postClosing(type);
        if (type == StreamType.INPUT || type == StreamType.INPUT_OUTPUT)
        {
            synchronized (this)
            {
                readState = ReadState.CLOSE;
                notify();
                logger.debug("local close, notified reader thread");
            }
        }
    }

    public int available()
    {
        synchronized (this)
        {
            return store == null ? 0 : store.remaining();
        }
    }

    private enum ReadState
    {
        READ, WAIT, TIMEOUT, REMOTE_CLOSE, CLOSE
    }
}
