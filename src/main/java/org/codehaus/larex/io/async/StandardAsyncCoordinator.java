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

package org.codehaus.larex.io.async;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Executor;

import org.codehaus.larex.io.RuntimeSocketClosedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * // TODO: implement read and write timeouts, between needs[Read|Write]() and [read|write]Ready()
 * // TODO: not quite: need to throw exceptions in the calling thread (for write at least)
 * @version $Revision: 903 $ $Date$
 */
public class StandardAsyncCoordinator implements AsyncCoordinator
{
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AsyncSelector selector;
    private final Executor threadPool;
    private final Runnable reader = new Reader();
    private volatile AsyncChannel channel;
    private volatile AsyncInterpreter interpreter;
    private volatile int readBufferSize = 1024;

    public StandardAsyncCoordinator(AsyncSelector selector, Executor threadPool)
    {
        this.selector = selector;
        this.threadPool = threadPool;
    }

    public void setAsyncChannel(AsyncChannel channel)
    {
        this.channel = channel;
    }

    public void setAsyncInterpreter(AsyncInterpreter interpreter)
    {
        this.interpreter = interpreter;
    }

    public void setReadBufferSize(int size)
    {
        this.readBufferSize = size;
    }

    public void open()
    {
        interpreter.onOpen();
    }

    public void readReady()
    {
        // Remove interest in further reads, otherwise the select loop will
        // continue to notify us that it is ready to read
        needsRead(false);
        // Dispatch the read to another thread
        threadPool.execute(reader);
    }

    public void writeReady()
    {
        // Remove interest in further writes, otherwise the select loop will
        // continue to notify us that it is ready to write
        needsWrite(false);
        // Notify the suspended thread that it can write some more
        channel.writeReady();
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
        interpreter.onRead(buffer);
    }

    public void write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        channel.write(buffer);
    }

    public void close()
    {
        channel.close();
    }

    public void onClose()
    {
        interpreter.onClose();
    }

    private class Reader implements Runnable
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
