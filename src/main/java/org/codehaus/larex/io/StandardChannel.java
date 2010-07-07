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
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StandardChannel implements Channel, Runnable
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final boolean debug = logger.isDebugEnabled();
    private final Selector selector;
    private final SocketChannel channel;
    private final Controller controller;
    private volatile int readAggressiveness = 2;
    private volatile int writeAggressiveness = 2;
    private volatile SelectionKey selectionKey;
    private volatile int interestOps;

    public StandardChannel(Selector selector, SocketChannel channel, Controller controller)
    {
        this.selector = selector;
        this.channel = channel;
        this.controller = controller;
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

    @Override
    public boolean register(final java.nio.channels.Selector nioSelector, final Selector.Listener listener) throws RuntimeSocketClosedException
    {
        Register task = new Register(nioSelector, listener);
        selector.submit(task);
        return task.result();
    }

    @Override
    public void update(int operations, boolean add) throws RuntimeSocketClosedException
    {
        int oldOperations = interestOps;
        int newOperations;
        if (add)
            newOperations = oldOperations | operations;
        else
            newOperations = oldOperations & ~operations;
        if (newOperations != oldOperations)
        {
            interestOps = newOperations;
            selector.submit(this);
        }
    }

    @Override
    public void run()
    {
        SelectionKey selectionKey = this.selectionKey;
        if (selectionKey == null)
        {
            if (debug)
                logger.debug("Ignoring update for already closed channel {}", channel);
            return;
        }

        try
        {
            int oldOperations = selectionKey.interestOps();
            int newOperations = interestOps;
            selectionKey.interestOps(newOperations);
            if (debug)
                logger.debug("Channel {} operations {} -> {}", new Object[]{this, oldOperations, newOperations});
        }
        catch (CancelledKeyException x)
        {
            logger.debug("Ignoring update for concurrently closed channel {}", channel);
        }
    }

    @Override
    public boolean unregister(java.nio.channels.Selector nioSelector, Selector.Listener listener)
    {
        Unregister task = new Unregister();
        selector.submit(task);
        return task.result();
    }

    @Override
    public boolean read(ByteBuffer buffer)
    {
        try
        {
            return readAggressively(channel, buffer);
        }
        catch (ClosedChannelException x)
        {
            controller.close(StreamType.INPUT_OUTPUT);
            throw new RuntimeSocketClosedException(x);
        }
        catch (IOException x)
        {
            controller.close(StreamType.INPUT_OUTPUT);
            throw new RuntimeIOException(x);
        }
    }

    protected boolean readAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
    {
        int aggressiveness = getReadAggressiveness();
        while (--aggressiveness >= 0)
        {
            int read = channel.read(buffer);
            if (read < 0)
                return true;
            if (!buffer.hasRemaining())
                break;
        }
        return false;
    }

    @Override
    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        try
        {
            return writeAggressively(channel, buffer);
        }
        catch (ClosedChannelException x)
        {
            logger.debug("Channel closed during write of {} bytes", buffer.remaining());
            controller.close(StreamType.INPUT_OUTPUT);
            throw new RuntimeSocketClosedException(x);
        }
        catch (IOException x)
        {
            logger.debug("Unexpected IOException", x);
            controller.close(StreamType.INPUT_OUTPUT);
            throw new RuntimeIOException(x);
        }
    }

    protected int writeAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
    {
        int aggressiveness = getWriteAggressiveness();
        int result = 0;
        while (--aggressiveness >= 0)
        {
            result += this.channel.write(buffer);
            if (!buffer.hasRemaining())
                break;
        }
        return result;
    }

    @Override
    public boolean isClosed(StreamType type)
    {
        switch (type)
        {
            case INPUT:
                return channel.socket().isInputShutdown();
            case OUTPUT:
                return channel.socket().isOutputShutdown();
            case INPUT_OUTPUT:
                return !channel.isOpen();
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void close(StreamType type)
    {
        if (isClosed(type))
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
                case INPUT_OUTPUT:
                    close();
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

    protected void close() throws IOException
    {
        Close task = new Close();
        selector.submit(task);
        task.await();
    }

    @Override
    public String toString()
    {
        return channel.toString();
    }

    private class Register implements Runnable
    {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final java.nio.channels.Selector selector;
        private final Selector.Listener listener;

        private Register(java.nio.channels.Selector selector, Selector.Listener listener)
        {
            this.selector = selector;
            this.listener = listener;
        }

        public void run()
        {
            try
            {
                selectionKey = channel.register(selector, 0, listener);
                listener.onOpen();
            }
            catch (ClosedChannelException x)
            {
                logger.debug("Ignoring registration of listener {} for closed channel {}", listener, channel);
            }
            finally
            {
                latch.countDown();
            }
        }

        private boolean result()
        {
            try
            {
                latch.await();
                return selectionKey != null;
            }
            catch (InterruptedException x)
            {
                throw new RuntimeIOException(x);
            }
        }
    }

    private class Unregister implements Runnable
    {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile boolean result;

        @Override
        public void run()
        {
            try
            {
                final SelectionKey selectionKey = StandardChannel.this.selectionKey;
                if (selectionKey != null)
                {
                    selectionKey.cancel();
                    result = true;
                }
            }
            finally
            {
                latch.countDown();
            }
        }

        public boolean result()
        {
            try
            {
                latch.await();
                return result;
            }
            catch (InterruptedException x)
            {
                throw new RuntimeIOException(x);
            }
        }
    }

    private class Close implements Runnable
    {
        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile IOException exception;

        @Override
        public void run()
        {
            try
            {
                final SelectionKey selectionKey = StandardChannel.this.selectionKey;
                if (selectionKey != null)
                {
                    selector.unregister(StandardChannel.this, (Selector.Listener)selectionKey.attachment());
                    StandardChannel.this.selectionKey = null;
                }
                channel.close();
            }
            catch (IOException x)
            {
                exception = x;
            }
            finally
            {
                latch.countDown();
            }
        }

        private void await()
        {
            try
            {
                latch.await();
                final IOException x = exception;
                if (x != null)
                    throw new RuntimeIOException(x);
            }
            catch (InterruptedException x)
            {
                throw new RuntimeIOException(x);
            }
        }
    }
}
