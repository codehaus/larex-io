package org.codehaus.larex.io;

import java.nio.ByteBuffer;

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

    public final int write(ByteBuffer buffer)
    {
        return writer.write(buffer);
    }
}
