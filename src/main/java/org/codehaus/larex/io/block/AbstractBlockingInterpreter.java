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

package org.codehaus.larex.io.block;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.Executor;

import org.codehaus.larex.io.RuntimeSocketClosedException;
import org.codehaus.larex.io.async.AsyncCoordinator;
import org.codehaus.larex.io.async.AsyncInterpreter;

/**
 * @version $Revision$ $Date$
 */
public abstract class AbstractBlockingInterpreter implements AsyncInterpreter, Runnable
{
    private final AsyncCoordinator coordinator;
    private final Executor threadPool;
    private ByteBuffer buffer;
    private State state = State.READ;

    public AbstractBlockingInterpreter(AsyncCoordinator coordinator, Executor threadPool)
    {
        this.coordinator = coordinator;
        this.threadPool = threadPool;
    }

    public void onOpen()
    {
        threadPool.execute(this);
    }

    public void onRead(ByteBuffer buffer)
    {
        synchronized (this)
        {
            this.buffer.put(buffer);
            state = State.READ;
            notify();
        }
    }

    public void onClose()
    {
        synchronized (this)
        {
            state = State.CLOSE;
            notify();
        }
    }

    protected int read(ByteBuffer buffer)
    {
        int start = buffer.position();
        coordinator.setReadBufferSize(buffer.remaining());
        synchronized (this)
        {
            // TODO: not quite: I can have a buffer to give to the user
            // I must check if the buffer is empty, and then if it's closed
            if (state == State.CLOSE)
                return -1;

            this.buffer = buffer;
            state = State.WAIT;
            coordinator.needsRead(true);
            while (state == State.WAIT)
            {
                try
                {
                    wait();
                }
                catch (InterruptedException x)
                {
                    close();
                    Thread.currentThread().interrupt();
                    throw new RuntimeSocketClosedException(new ClosedByInterruptException());
                }
            }

            if (state == State.CLOSE)
                return -1;

            return buffer.position() - start;
        }
    }

    protected ByteBuffer copy(ByteBuffer source)
    {
        ByteBuffer result = ByteBuffer.allocate(source.remaining());
        result.put(source);
        result.flip();
        return result;
    }

    protected void write(ByteBuffer buffer)
    {
        coordinator.write(buffer);
    }

    protected void close()
    {
        coordinator.close();
    }

    private enum State
    {
        READ, WAIT, CLOSE
    }
}
