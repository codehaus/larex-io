package org.codehaus.larex.io;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockingReader
{
    private static final Logger logger = LoggerFactory.getLogger(BlockingConnection.class);

    private final Controller controller;
    /* Guarded by #this */
    private ByteBuffer store;
    /* Guarded by #this */
    private ReadState readState = ReadState.WAIT;

    public BlockingReader(Controller controller)
    {
        this.controller = controller;
    }

    public boolean fill(ByteBuffer buffer)
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

    public int read(ByteBuffer buffer)
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
                    controller.needsRead(true);
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
                throw new RuntimeSocketTimeoutException(); // TODO: must close ?
            else if (readState == ReadState.CLOSE)
                throw new RuntimeSocketClosedException();
            else if (readState == ReadState.REMOTE_CLOSE)
                return -1;

            readState = ReadState.WAIT;
            controller.needsRead(true);
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
                    controller.close(StreamType.INPUT_OUTPUT);
                    Thread.currentThread().interrupt();
                    throw new RuntimeSocketClosedException(new ClosedByInterruptException());
                }
            }
            logger.debug("read notified, state {}", readState);
            return read(buffer);
        }
    }

    public void readTimeoutEvent()
    {
        synchronized (this)
        {
            readState = ReadState.TIMEOUT;
            notify();
            logger.debug("read timeout, notified reader thread");
        }
    }

    public void remoteCloseEvent()
    {
        synchronized (this)
        {
            readState = ReadState.REMOTE_CLOSE;
            notify();
            logger.debug("remote close, notified reader thread");
        }
    }

    public void closeEvent()
    {
        synchronized (this)
        {
            readState = ReadState.CLOSE;
            notify();
            logger.debug("local close, notified reader thread");
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
