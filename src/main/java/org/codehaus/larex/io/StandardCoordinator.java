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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardCoordinator implements Coordinator
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Selector selector;
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

    public StandardCoordinator(Selector selector, ByteBuffers byteBuffers, Executor threadPool)
    {
        this.selector = selector;
        this.byteBuffers = byteBuffers;
        this.threadPool = threadPool;
        headInterceptor.setNext(tailInterceptor);
    }

    protected Selector getSelector()
    {
        return selector;
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

    public void setChannel(Channel channel)
    {
        this.channel = channel;
    }

    protected Connection getConnection()
    {
        return connection;
    }

    public void setConnection(Connection connection)
    {
        this.connection = connection;
    }

    protected int getReadBufferSize()
    {
        return readBufferSize;
    }

    public void setReadBufferSize(int size)
    {
        this.readBufferSize = size;
    }

    public void addInterceptor(Interceptor interceptor)
    {
        Interceptor target = headInterceptor;
        while (target.getNext() != tailInterceptor)
            target = target.getNext();
        target.setNext(interceptor);
        interceptor.setNext(tailInterceptor);
    }

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

    public void onOpen()
    {
        getInterceptor().onPrepare();
        getThreadPool().execute(onOpenAction);
    }

    public void onReadReady()
    {
        // Remove interest in further reads, otherwise the select loop will
        // continue to notify us that it is ready to read
        needsRead(false);
        // Dispatch the read to another thread
        getThreadPool().execute(onReadAction);
    }

    public void onWriteReady()
    {
        // Remove interest in further writes, otherwise the select loop will
        // continue to notify us that it is ready to write
        needsWrite(false);
        // Notify the suspended thread that it can write some more
        getThreadPool().execute(onWriteAction);
    }

    public void onClose()
    {
        getThreadPool().execute(onCloseAction);
    }

    public void needsRead(boolean needsRead)
    {
        getSelector().update(getChannel(), SelectionKey.OP_READ, needsRead);
    }

    public void needsWrite(boolean needsWrite)
    {
        getSelector().update(getChannel(), SelectionKey.OP_WRITE, needsWrite);
    }

    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        return getInterceptor().write(buffer);
    }

    protected void onRemoteClose()
    {
        try
        {
            getInterceptor().onRemoteClose();
        }
        finally
        {
            close();
        }
    }

    public void close(StreamType type)
    {
        getInterceptor().close(type);
    }

    public void close()
    {
        getInterceptor().onClosing();
        try
        {
            getInterceptor().close();
        }
        finally
        {
            getInterceptor().onClosed();
        }
    }

    protected void onOpenAction()
    {
        getInterceptor().onOpen();
    }

    protected void onReadAction()
    {
        int read;
        boolean closed;
        int readBufferSize = getReadBufferSize();
        ByteBuffer buffer = getByteBuffers().acquire(readBufferSize, false);
        try
        {
            // The buffer can be smaller than the data available, so read until we cannot read anymore.
            while (true)
            {
                int start = buffer.position();
                closed = getChannel().read(buffer);
                read = buffer.position() - start;

                if (read > 0)
                {
                    if (logger.isDebugEnabled())
                        logger.debug("Channel {} read {} bytes into {}", new Object[]{getChannel(), read, buffer});
                    buffer.flip();
                    onRead(buffer);
                    buffer.clear();
                    buffer.limit(readBufferSize);
                }

                if (read == 0 || closed)
                    break;
            }
        }
        finally
        {
            getByteBuffers().release(buffer);
        }

        if (closed)
        {
            // TODO: improve this: the if may not be needed
            if (!getChannel().isClosed(StreamType.INPUT))
            {
                if (logger.isDebugEnabled())
                    logger.debug("Channel {} closed remotely", getChannel());
                onRemoteClose();
            }
            else
            {
                // Input was explicitly closed
                throw new RuntimeSocketClosedException();
            }
        }
        else
        {
            // We read 0 bytes, we need to re-register for read interest
            needsRead(true);
        }
    }

    protected void onWriteAction()
    {
        getInterceptor().onWrite();
    }

    protected void onCloseAction()
    {
        close();
    }

    protected void onRead(ByteBuffer buffer)
    {
        try
        {
            getInterceptor().onRead(buffer);
        }
        catch (Exception x)
        {
            logger.info("Unexpected exception", x);
        }
    }

    protected Interceptor getInterceptor()
    {
        return headInterceptor;
    }

    private class OnOpenAction implements Runnable
    {
        public void run()
        {
            onOpenAction();
        }
    }

    private class OnReadAction implements Runnable
    {
        public void run()
        {
            try
            {
                onReadAction();
            }
            catch (RuntimeSocketClosedException x)
            {
                logger.debug("Could not read, channel has been closed", x);
            }
            catch (RuntimeIOException x)
            {
                logger.debug("Could not read", x);
            }
        }
    }

    private class OnWriteAction implements Runnable
    {
        public void run()
        {
            try
            {
                onWriteAction();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception", x);
            }
        }
    }

    private class OnCloseAction implements Runnable
    {
        public void run()
        {
            try
            {
                onCloseAction();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception", x);
            }
        }
    }

    private class StandardInterceptor implements Interceptor
    {
        public Interceptor getNext()
        {
            return null;
        }

        public void setNext(Interceptor interceptor)
        {
            throw new UnsupportedOperationException();
        }

        public void onPrepare()
        {
            getConnection().prepareEvent();
        }

        public void onOpen()
        {
            try
            {
                getConnection().openEvent();
            }
            catch (Exception x)
            {
                logger.debug("Unexpected exception", x);
            }
        }

        public void onReadTimeout()
        {
            try
            {
                getConnection().readTimeoutEvent();
            }
            catch (Exception x)
            {
                logger.debug("Unexpected exception", x);
            }
        }

        public void onRead(ByteBuffer buffer)
        {
            try
            {
                getConnection().readEvent(buffer);
            }
            catch (Exception x)
            {
                logger.debug("Unexpected exception", x);
            }
        }

        public void onWrite()
        {
            try
            {
                getConnection().writeEvent();
            }
            catch (Exception x)
            {
                logger.debug("Unexpected exception", x);
            }
        }

        public void onWriteTimeout()
        {
            try
            {
                getConnection().writeTimeoutEvent();
            }
            catch (Exception x)
            {
                logger.debug("Unexpected exception", x);
            }
        }

        public int write(ByteBuffer buffer)
        {
            return getChannel().write(buffer);
        }

        public void onRemoteClose()
        {
            try
            {
                getConnection().remoteCloseEvent();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception", x);
            }
        }

        public void onClosing()
        {
            try
            {
                getConnection().closingEvent();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception", x);
            }
        }

        public void onClosed()
        {
            try
            {
                getConnection().closedEvent();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception", x);
            }
        }

        public void close(StreamType type)
        {
            getChannel().close(type);
        }

        public void close()
        {
            getChannel().close();
        }
    }
}
