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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision: 903 $ $Date$
 */
public class StandardChannel implements Channel
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final boolean debug = logger.isDebugEnabled();
    private final SocketChannel channel;
    private final Controller controller;
    private volatile int readAggressiveness;
    private volatile int writeAggressiveness;
    private volatile SelectionKey selectionKey;

    public StandardChannel(SocketChannel channel, Controller controller)
    {
        this.channel = channel;
        this.controller = controller;
        setReadAggressiveness(2);
        setWriteAggressiveness(2);
    }

    public int getReadAggressiveness()
    {
        return readAggressiveness;
    }

    public void setReadAggressiveness(int readAggressiveness)
    {
        this.readAggressiveness = readAggressiveness;
    }

    public int getWriteAggressiveness()
    {
        return writeAggressiveness;
    }

    public void setWriteAggressiveness(int writeAggressiveness)
    {
        this.writeAggressiveness = writeAggressiveness;
    }

    public void register(java.nio.channels.Selector selector, Selector.Listener listener) throws RuntimeSocketClosedException
    {
        try
        {
            selectionKey = channel.register(selector, 0, listener);
        }
        catch (ClosedChannelException x)
        {
            throw new RuntimeSocketClosedException(x);
        }
    }

    public void update(int operations, boolean add) throws RuntimeSocketClosedException
    {
        try
        {
            SelectionKey selectionKey = this.selectionKey;
            int oldOperations = selectionKey.interestOps();
            int newOperations;
            if (add)
                newOperations = oldOperations | operations;
            else
                newOperations = oldOperations & ~operations;
            if (newOperations != oldOperations)
                selectionKey.interestOps(newOperations);
            if (debug)
                logger.debug("Channel {} operations {} -> {}", new Object[]{this, oldOperations, newOperations});
        }
        catch (CancelledKeyException x)
        {
            throw new RuntimeSocketClosedException(x);
        }
    }

    public boolean read(ByteBuffer buffer)
    {
        try
        {
            return readAggressively(channel, buffer);
        }
        catch (ClosedChannelException x)
        {
            controller.close();
            throw new RuntimeSocketClosedException(x);
        }
        catch (IOException x)
        {
            controller.close();
            throw new RuntimeIOException(x);
        }
    }

    protected boolean readAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
    {
        int readAggressiveness = getReadAggressiveness();
        for (int aggressiveness = 0; aggressiveness < readAggressiveness; ++aggressiveness)
        {
            int read = channel.read(buffer);
            if (read < 0)
                return true;
        }
        return false;
    }

    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        try
        {
            return writeAggressively(channel, buffer);
        }
        catch (ClosedChannelException x)
        {
            logger.debug("Channel closed during write of {} bytes", buffer.remaining());
            controller.close();
            throw new RuntimeSocketClosedException(x);
        }
        catch (IOException x)
        {
            logger.debug("Unexpected IOException", x);
            controller.close();
            throw new RuntimeIOException(x);
        }
    }

    protected int writeAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
    {
        int writeAggressiveness = getWriteAggressiveness();
        int result = 0;
        for (int aggressiveness = 0; aggressiveness < writeAggressiveness; ++aggressiveness)
        {
            result += this.channel.write(buffer);
        }
        return result;
    }

    public boolean isClosed(StreamType type)
    {
        switch (type)
        {
            case INPUT:
                return channel.socket().isInputShutdown();
            case OUTPUT:
                return channel.socket().isOutputShutdown();
            default:
                throw new IllegalStateException();
        }
    }

    public void close(StreamType type)
    {
        if (isClosed() || isClosed(type))
            return;

        if (debug)
            logger.debug("Channel {} closing {}", this, type);

        try
        {
            switch (type)
            {
                case INPUT:
                    channel.socket().shutdownInput();
                    break;
                case OUTPUT:
                    channel.socket().shutdownOutput();
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    public boolean isClosed()
    {
        return !channel.isOpen();
    }

    public void close()
    {
        if (isClosed())
            return;

        if (debug)
            logger.debug("Channel {} closing", this);

        try
        {
            SelectionKey selectionKey = this.selectionKey;
            if (selectionKey != null)
                selectionKey.cancel();

            channel.close();
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    @Override
    public String toString()
    {
        return channel.toString();
    }
}
