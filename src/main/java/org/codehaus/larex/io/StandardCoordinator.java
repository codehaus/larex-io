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
    private final Runnable onReadyCommand = new OnReadyCommand();
    private final Runnable readCommand = new ReadCommand();
    private final Runnable onWriteCommand = new OnWriteCommand();
    private final Runnable closeCommand = new CloseCommand();
    private volatile Channel channel;
    private volatile Connection connection;
    private volatile int readBufferSize = 1024;

    public StandardCoordinator(Selector selector, ByteBuffers byteBuffers, Executor threadPool)
    {
        this.selector = selector;
        this.byteBuffers = byteBuffers;
        this.threadPool = threadPool;
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

    public void onOpen()
    {
        connection.onOpen();
        getThreadPool().execute(onReadyCommand);
    }

    public void onReadReady()
    {
        // Remove interest in further reads, otherwise the select loop will
        // continue to notify us that it is ready to read
        needsRead(false);
        // Dispatch the read to another thread
        getThreadPool().execute(readCommand);
    }

    public void onWriteReady()
    {
        // Remove interest in further writes, otherwise the select loop will
        // continue to notify us that it is ready to write
        needsWrite(false);
        // Notify the suspended thread that it can write some more
        getThreadPool().execute(onWriteCommand);
    }

    public void onClose()
    {
        getThreadPool().execute(closeCommand);
    }

    public void needsRead(boolean needsRead)
    {
        getSelector().update(channel, SelectionKey.OP_READ, needsRead);
    }

    public void needsWrite(boolean needsWrite)
    {
        getSelector().update(channel, SelectionKey.OP_WRITE, needsWrite);
    }

    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        return channel.write(buffer);
    }

    protected void onRemoteClose()
    {
        try
        {
            connection.onRemoteClose();
        }
        catch (Exception x)
        {
            logger.info("Unexpected exception", x);
        }
        finally
        {
            close();
        }
    }

    public void close(ChannelStreamType type)
    {
        channel.close(type);
    }

    public void close()
    {
        try
        {
            connection.onClose();
        }
        catch (Exception x)
        {
            logger.info("Unexpected exception", x);
        }

        try
        {
            channel.close();
        }
        finally
        {
            onClosed();
        }
    }

    public void onClosed()
    {
        try
        {
            connection.onClosed();
        }
        catch (Exception x)
        {
            logger.info("Unexpected exception", x);
        }
    }

    protected void onReady()
    {
        connection.onReady();
    }

    protected void onWrite()
    {
        connection.onWrite();
    }

    protected void read()
    {
        int read;
        boolean closed;
        ByteBuffer buffer = getByteBuffers().acquire(getReadBufferSize(), false);
        try
        {
            int start = buffer.position();
            closed = channel.read(buffer);
            read = buffer.position() - start;

            if (read > 0)
            {
                if (logger.isDebugEnabled())
                    logger.debug("Channel {} read {} bytes into {}", new Object[]{channel, read, buffer});
                buffer.flip();
                onRead(buffer);
            }
        }
        finally
        {
            getByteBuffers().release(buffer);
        }

        if (closed)
        {
            if (!channel.isClosed(ChannelStreamType.INPUT))
            {
                if (logger.isDebugEnabled())
                    logger.debug("Channel {} closed remotely", channel);
                onRemoteClose();
            }
            else
            {
                // Input was explicitly closed
                throw new RuntimeSocketClosedException();
            }
        }
        else if (read == 0)
        {
            // We read 0 bytes, we need to re-register for read interest
            needsRead(true);
        }
    }

    protected void onRead(ByteBuffer buffer)
    {
        try
        {
            connection.onRead(buffer);
        }
        catch (Exception x)
        {
            logger.info("Unexpected exception", x);
        }
    }

    private class OnReadyCommand implements Runnable
    {
        public void run()
        {
            onReady();
        }
    }

    private class ReadCommand implements Runnable
    {
        public void run()
        {
            try
            {
                read();
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

    private class OnWriteCommand implements Runnable
    {
        public void run()
        {
            onWrite();
        }
    }

    private class CloseCommand implements Runnable
    {
        public void run()
        {
            try
            {
                close();
            }
            catch (Exception x)
            {
                logger.info("Unexpected exception", x);
            }
        }
    }
}
