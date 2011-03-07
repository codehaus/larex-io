package org.codehaus.larex.io;

import java.nio.ByteBuffer;

public class EmptyController implements Controller
{
    @Override
    public void setReadBufferSize(int size)
    {
    }

    @Override
    public void addInterceptor(Interceptor interceptor)
    {
    }

    @Override
    public boolean removeInterceptor(Interceptor interceptor)
    {
        return false;
    }

    @Override
    public void needsRead(boolean needsRead)
    {
    }

    @Override
    public void needsWrite(boolean needsWrite)
    {
    }

    @Override
    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        return 0;
    }

    @Override
    public void close(StreamType type)
    {
    }

    @Override
    public boolean isClosed(StreamType type)
    {
        return false;
    }

    protected int write(ByteBuffer buffer, int count)
    {
        buffer.position(buffer.position() + count);
        return count;
    }
}
