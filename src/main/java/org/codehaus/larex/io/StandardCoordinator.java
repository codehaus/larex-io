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
import java.nio.channels.SelectionKey;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardCoordinator implements Coordinator
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Reactor reactor;
    private final ByteBuffers byteBuffers;
    private final Executor threadPool;
    private final Runnable onOpenAction = new OnOpenAction();
    private final Runnable onReadAction = new OnReadAction();
    private final Runnable onWriteAction = new OnWriteAction();
    private final Runnable onCloseAction = new OnCloseAction();
    private final Interceptor headInterceptor = new Interceptor.Forwarder();
    private final Interceptor tailInterceptor = new StandardInterceptor();
    private volatile Channel channel;
    private volatile Connection connection;
    private volatile int readBufferSize = 1024;

    public StandardCoordinator(Reactor reactor, ByteBuffers byteBuffers, Executor threadPool)
    {
        this.reactor = reactor;
        this.byteBuffers = byteBuffers;
        this.threadPool = threadPool;
        headInterceptor.setNext(tailInterceptor);
    }

    protected Reactor getReactor()
    {
        return reactor;
    }

    protected ByteBuffers getByteBuffers()
    {
        return byteBuffers;
    }

    protected Executor getThreadPool()
    {
        return threadPool;
    }

    protected Channel getChannel()
    {
        return channel;
    }

    @Override
    public void setChannel(Channel channel)
    {
        this.channel = channel;
    }

    protected Connection getConnection()
    {
        return connection;
    }

    @Override
    public void setConnection(Connection connection)
    {
        this.connection = connection;
    }

    protected int getReadBufferSize()
    {
        return readBufferSize;
    }

    @Override
    public void setReadBufferSize(int size)
    {
        this.readBufferSize = size;
    }

    @Override
    public void addInterceptor(Interceptor interceptor)
    {
        Interceptor target = headInterceptor;
        while (target.getNext() != tailInterceptor)
            target = target.getNext();
        target.setNext(interceptor);
        interceptor.setNext(tailInterceptor);
    }

    @Override
    public boolean removeInterceptor(Interceptor interceptor)
    {
        Interceptor target = headInterceptor;
        while (target.getNext() != tailInterceptor && target.getNext() != interceptor)
            target = target.getNext();
        if (target.getNext() == interceptor)
        {
            target.setNext(interceptor.getNext());
            interceptor.setNext(null);
            return true;
        }
        return false;
    }

    @Override
    public void onOpen()
    {
        dispatch(onOpenAction);
    }

    protected void processOnOpen()
    {
        getInterceptor().onOpen();
        needsRead(true);
    }

    @Override
    public void onReadReady()
    {
        // Remove interest in further reads, otherwise the select loop will
        // continue to notify us that it is ready to read
        needsRead(false);
        // Dispatch the read to another thread
        dispatch(onReadAction);
    }

    protected void processOnRead()
    {
        boolean debug = logger.isDebugEnabled();

        int read;
        int totalRead = 0;
        boolean closed;
        boolean readMore = true;

        readBegin();

        // Reads are performed by the JVM in a temporary direct buffer, then copied
        // into the buffer passed to SocketChannel.read(Buffer).
        // In general there is no big savings in having the read buffer to be direct
        // since a copy of the data is performed anyway (although copying from direct
        // to direct could be optimized by the JVM).
        // There is however a big saving in writing using a direct buffer, since the
        // data does not need to be copied to a temporary direct buffer.
        // So we use a direct buffer here to read, to exploit the possible JVM
        // optimization, and to allow the user code to use this buffer as the write
        // buffer (read from it, clear it, fill it with data, write it).
        int readBufferSize = getReadBufferSize();
        ByteBuffer buffer = getByteBuffers().acquire(readBufferSize, true);
        try
        {
            // The buffer can be smaller than the data available,
            // therefore we read until we cannot read anymore.
            while (true)
            {
                int start = buffer.position();
                closed = read(buffer);
                read = buffer.position() - start;
                totalRead += read;

                if (read > 0)
                {
                    if (debug)
                        logger.debug("Channel {} read {} bytes into {}", new Object[]{getChannel(), read, buffer});
                    buffer.flip();
                    readMore = onRead(buffer);
                    buffer.clear();
                    buffer.limit(readBufferSize);
                }

                if (!readMore || read == 0 || closed)
                    break;
            }
        }
        finally
        {
            getByteBuffers().release(buffer);

            readEnd(totalRead, readMore);
        }

        if (closed)
        {
            // If the input is closed by user code, reading returns -1,
            // but that's different from a remote close that also returns -1
            if (channel.isClosed(StreamType.INPUT))
            {
                if (debug)
                    logger.debug("Channel {} closed locally", getChannel());
            }
            else
            {
                if (debug)
                    logger.debug("Channel {} closed remotely", getChannel());
                onRemoteClose();
            }
            needsRead(false);
        }
        else
        {
            needsRead(readMore);
        }
    }

    protected void readBegin()
    {
    }

    protected boolean read(ByteBuffer buffer)
    {
        return getChannel().read(buffer);
    }

    protected void readEnd(int read, boolean needsRead)
    {
    }

    @Override
    public void onWriteReady()
    {
        // Remove interest in further writes, otherwise the select loop will
        // continue to notify us that it is ready to write
        needsWrite(false);
        // Notify the suspended thread that it can write some more
        dispatch(onWriteAction);
    }

    protected void processOnWrite()
    {
        getInterceptor().onWrite();
    }

    @Override
    public void timeoutRead()
    {
    }

    @Override
    public void timeoutWrite()
    {
    }

    @Override
    public void onClose()
    {
        dispatch(onCloseAction);
    }

    protected void processOnClose()
    {
        close(StreamType.INPUT_OUTPUT);
    }

    @Override
    public void needsRead(boolean needsRead)
    {
        getReactor().update(getChannel(), SelectionKey.OP_READ, needsRead);
    }

    @Override
    public void needsWrite(boolean needsWrite)
    {
        getReactor().update(getChannel(), SelectionKey.OP_WRITE, needsWrite);
    }

    @Override
    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        writeBegin();
        int written = -1;
        try
        {
            written = doWrite(buffer);
            return written;
        }
        finally
        {
            writeEnd(written, buffer.hasRemaining());
        }
    }

    protected void writeBegin()
    {
    }

    protected int doWrite(ByteBuffer buffer)
    {
        return getInterceptor().write(buffer);
    }

    protected void writeEnd(int written, boolean needsWrite)
    {
    }

    protected void onRemoteClose()
    {
        try
        {
            getInterceptor().onRemoteClose();
        }
        finally
        {
            close(StreamType.INPUT_OUTPUT);
        }
    }

    @Override
    public void close(StreamType type)
    {
        if (type == null)
            throw new NullPointerException();

        getInterceptor().onClosing(type);
        try
        {
            getInterceptor().close(type);
        }
        finally
        {
            getInterceptor().onClosed(type);
        }
    }

    protected boolean onRead(ByteBuffer buffer)
    {
        return getInterceptor().onRead(buffer);
    }

    protected Interceptor getInterceptor()
    {
        return headInterceptor;
    }

    protected void dispatch(Runnable action)
    {
        try
        {
            getThreadPool().execute(action);
        }
        catch (RejectedExecutionException x)
        {
            logger.debug("", x);
        }
    }

    private class OnOpenAction implements Runnable
    {
        @Override
        public void run()
        {
            processOnOpen();
        }
    }

    private class OnReadAction implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                processOnRead();
            }
            catch (Exception x)
            {
                logger.debug("Could not process read", x);
            }
        }
    }

    private class OnWriteAction implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                processOnWrite();
            }
            catch (Exception x)
            {
                logger.info("Could not process write", x);
            }
        }
    }

    private class OnCloseAction implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                processOnClose();
            }
            catch (Exception x)
            {
                logger.info("Could not process close", x);
            }
        }
    }

    private class StandardInterceptor implements Interceptor
    {
        @Override
        public Interceptor getNext()
        {
            return null;
        }

        @Override
        public void setNext(Interceptor interceptor)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onOpen()
        {
            try
            {
                getConnection().openEvent();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception in Interceptor.onOpen", x);
            }
        }

        @Override
        public void onReadTimeout()
        {
            try
            {
                getConnection().readTimeoutEvent();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception in Interceptor.onReadTimeout", x);
            }
        }

        @Override
        public boolean onRead(ByteBuffer buffer)
        {
            try
            {
                return getConnection().readEvent(buffer);
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception in Interceptor.onRead", x);
                return true;
            }
        }

        @Override
        public void onWrite()
        {
            try
            {
                getConnection().writeEvent();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception in Interceptor.onWrite", x);
            }
        }

        @Override
        public void onWriteTimeout()
        {
            try
            {
                getConnection().writeTimeoutEvent();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception in Interceptor.onWriteTimeout", x);
            }
        }

        @Override
        public int write(ByteBuffer buffer)
        {
            return getChannel().write(buffer);
        }

        @Override
        public void onRemoteClose()
        {
            try
            {
                getConnection().remoteCloseEvent();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception in Interceptor.onRemoteClose", x);
            }
        }

        @Override
        public void onClosing(StreamType type)
        {
            try
            {
                getConnection().closingEvent(type);
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception in Interceptor.onClosing", x);
            }
        }

        @Override
        public void onClosed(StreamType type)
        {
            try
            {
                getConnection().closedEvent(type);
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception in Interceptor.onClosed", x);
            }
        }

        @Override
        public void close(StreamType type)
        {
            getChannel().close(type);
        }
    }
}
