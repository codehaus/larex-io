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
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Selector selector;
    private final Executor threadPool;
    private final Runnable readCommand = new ReadCommand();
    private volatile Channel channel;
    private volatile Connection connection;
    private volatile int readBufferSize = 1024;

    public StandardCoordinator(Selector selector, Executor threadPool)
    {
        this.selector = selector;
        this.threadPool = threadPool;
    }

    protected Selector getSelector()
    {
        return selector;
    }

    protected Executor getThreadPool()
    {
        return threadPool;
    }

    protected Channel getAsyncChannel()
    {
        return channel;
    }

    public void setAsyncChannel(Channel channel)
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
    }

    public void onReadReady()
    {
        // Remove interest in further reads, otherwise the select loop will
        // continue to notify us that it is ready to read
        needsRead(false);
        // Dispatch the read to another thread
        threadPool.execute(readCommand);
    }

    public void onWriteReady()
    {
        // Remove interest in further writes, otherwise the select loop will
        // continue to notify us that it is ready to write
        needsWrite(false);
        // Notify the suspended thread that it can write some more
        connection.onWrite();
    }

    public void needsRead(boolean needsRead)
    {
        selector.update(channel, SelectionKey.OP_READ, needsRead);
    }

    public void needsWrite(boolean needsWrite)
    {
        selector.update(channel, SelectionKey.OP_WRITE, needsWrite);
    }

    public void onRead(ByteBuffer buffer)
    {
        connection.onRead(buffer);
    }

    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        return channel.write(buffer);
    }

    public void onClose()
    {
        try
        {
            connection.onClose();
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

    public void onRemoteClose()
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

    public void close()
    {
        selector.close(channel);
    }

    private class ReadCommand implements Runnable
    {
        public void run()
        {
            try
            {
                channel.read(readBufferSize);
            }
            catch (RuntimeSocketClosedException x)
            {
                logger.debug("Could not read, channel has been closed");
            }
        }
    }
}
