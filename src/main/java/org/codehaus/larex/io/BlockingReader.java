package org.codehaus.larex.io;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A helper class that implements blocking reads via {@link #read(ByteBuffer)}.</p>
 * <p>Read events copy the read bytes into a buffer that is then copied into the
 * buffer passed as parameter to {@link #read(ByteBuffer)}.</p>
 */
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

    /**
     * <p>Method to be invoked when the underlying connection has read bytes.</p>
     * <p>This method normally returns false, so that there is backpressure
     * on reads if the user code does not perform any read.</p>
     *
     * @param buffer the buffer containing the bytes read
     * @return whether to set read interest to receive further read events
     * @see #read(ByteBuffer)
     */
    public boolean readEvent(ByteBuffer buffer)
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
     * <p>Method to be invoked to read data into the given buffer.</p>
     *
     * @param buffer the buffer to read data into
     * @return the number of bytes read, or -1 if the remote peer has closed the connection
     * @see #readEvent(ByteBuffer)
     */
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

    /**
     * <p>Method to be invoked when the read timed out.</p>
     */
    public void readTimeoutEvent()
    {
        synchronized (this)
        {
            readState = ReadState.TIMEOUT;
            notify();
            logger.debug("read timeout, notified reader thread");
        }
    }

    /**
     * <p>Method to be invoked when the remote peer has closed the underlying connection.</p>
     */
    public void remoteCloseEvent()
    {
        synchronized (this)
        {
            readState = ReadState.REMOTE_CLOSE;
            notify();
            logger.debug("remote close, notified reader thread");
        }
    }

    /**
     * <p>Method to be invoked when the connection is about to be closed.</p>
     */
    public void closingEvent()
    {
        synchronized (this)
        {
            readState = ReadState.CLOSE;
            notify();
            logger.debug("local close, notified reader thread");
        }
    }

    /**
     * @return the number of bytes available to read without waiting
     */
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
