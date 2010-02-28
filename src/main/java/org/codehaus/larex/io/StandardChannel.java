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
    private final Coordinator coordinator;
    private final ByteBuffers byteBuffers;
    private volatile int readAggressiveness = 2;
    private volatile int writeAggressiveness = 2;
    private volatile SelectionKey selectionKey;

    public StandardChannel(SocketChannel channel, Coordinator coordinator, ByteBuffers byteBuffers)
    {
        this.channel = channel;
        this.coordinator = coordinator;
        this.byteBuffers = byteBuffers;
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
            int oldOperations = selectionKey.interestOps();
            if (add)
                selectionKey.interestOps(oldOperations | operations);
            else
                selectionKey.interestOps(oldOperations & ~operations);
            int newOperations = selectionKey.interestOps();
            if (debug)
                logger.debug("Channel {} operations {} -> {}", new Object[]{this, oldOperations, newOperations});
        }
        catch (CancelledKeyException x)
        {
            throw new RuntimeSocketClosedException(x);
        }
    }

    public void read(int readBufferSize) throws RuntimeSocketClosedException
    {
        try
        {
            int read;
            boolean closed;
            ByteBuffer buffer = byteBuffers.acquire(readBufferSize, false);
            try
            {
                int start = buffer.position();
                closed = readAggressively(channel, buffer);
                read = buffer.position() - start;

                if (read > 0)
                {
                    if (debug)
                        logger.debug("Channel {} read {} bytes into {}", new Object[]{this, read, buffer});
                    buffer.flip();
                    coordinator.onRead(buffer);
                }
            }
            finally
            {
                byteBuffers.release(buffer);
            }

            if (closed)
            {
                if (!channel.socket().isInputShutdown())
                {
                    if (debug)
                        logger.debug("Channel {} closed remotely", this);
                    coordinator.onRemoteClose();
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
                coordinator.needsRead(true);
            }
        }
        catch (ClosedChannelException x)
        {
            close();
            throw new RuntimeSocketClosedException(x);
        }
        catch (IOException x)
        {
            close();
            throw new RuntimeIOException(x);
        }
    }

    protected boolean readAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
    {
        int readAggressiveness = this.readAggressiveness;
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
            close();
            throw new RuntimeSocketClosedException(x);
        }
        catch (IOException x)
        {
            logger.debug("Unexpected IOException", x);
            close();
            throw new RuntimeIOException(x);
        }
    }

    protected int writeAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
    {
        int writeAggressiveness = this.writeAggressiveness;
        int result = 0;
        for (int aggressiveness = 0; aggressiveness < writeAggressiveness; ++aggressiveness)
        {
            result += this.channel.write(buffer);
        }
        return result;
    }

    public void close(CloseType type)
    {
        if (!channel.isOpen())
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

    public void close()
    {
        if (!channel.isOpen())
            return;

        try
        {
            if (selectionKey != null)
                selectionKey.cancel();

            if (debug)
                logger.debug("Channel {} closing", this);

            channel.close();
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
        finally
        {
            coordinator.onClosed();
        }
    }

    @Override
    public String toString()
    {
        return channel.toString();
    }
}
