package org.codehaus.larex.io;

import java.nio.ByteBuffer;

/**
 * <p>Partial implementation of {@link Connection} that provides buffering writes
 * functionalities and inherits close functionalities.</p>
 * <p>Writes are buffered so that the buffer to be written is always fully consumed
 * but in the event that it cannot be fully written to the underlying connection
 * the remaining bytes are buffered and written on first occasion.</p>
 */
public abstract class WritableConnection extends ClosableConnection
{
    private final NonBlockingWriter writer;

    public WritableConnection(Controller controller)
    {
        super(controller);
        this.writer = new NonBlockingWriter(controller);
    }

    @Override
    void doOnWrite()
    {
        super.doOnWrite();
        writer.writeReadyEvent();
    }

    @Override
    void doOnWriteTimeout()
    {
        super.doOnWriteTimeout();
        writer.writeTimeoutEvent();
    }

    @Override
    void doOnClosing(StreamType type)
    {
        super.doOnClosing(type);
        if (type == StreamType.OUTPUT || type == StreamType.INPUT_OUTPUT)
            writer.closingEvent();
    }

    /**
     * <p>Writes the bytes in the given buffer with non-blocking semantic.</p>
     * <p>The given buffer will be fully consumed, either because it has been
     * fully written or because the remaining bytes have been copied into a
     * temporary buffer to be written later; the buffer is therefore disposable
     * upon return from this method.</p>
     *
     * @param buffer the buffer to write
     * @return the number of bytes of the given buffer that have been written
     */
    public final int write(ByteBuffer buffer)
    {
        return writer.write(buffer);
    }
}
