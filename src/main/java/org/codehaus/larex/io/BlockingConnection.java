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
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.Executor;

/**
 * @version $Revision$ $Date$
 */
public abstract class BlockingConnection extends AbstractConnection implements Runnable
{
    private final Executor threadPool;
    private ByteBuffer buffer;
    private State state = State.WAIT;

    public BlockingConnection(Coordinator coordinator, Executor threadPool)
    {
        super(coordinator);
        this.threadPool = threadPool;
    }

    public abstract void run();

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

    public void onReadTimeout()
    {
        synchronized (this)
        {
            state = State.TIMEOUT;
            notify();
        }
    }

    protected int read(ByteBuffer buffer)
    {
        int start = buffer.position();
        getCoordinator().setReadBufferSize(buffer.remaining());

        synchronized (this)
        {
            if (state == State.REMOTE_CLOSE)
                return -1;

            this.buffer = buffer;
            state = State.WAIT;
            getCoordinator().needsRead(true);
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

            if (buffer.position() == start)
            {
                if (state == State.TIMEOUT)
                    throw new RuntimeSocketTimeoutException();
                else if (state == State.CLOSE)
                    throw new RuntimeSocketClosedException();
                else if (state == State.REMOTE_CLOSE)
                    return -1;
            }
        }

        return buffer.position() - start;
    }

    public void onRemoteClose()
    {
        synchronized (this)
        {
            state = State.REMOTE_CLOSE;
            notify();
        }
        super.close();
    }

    @Override
    public void close()
    {
        synchronized (this)
        {
            state = State.CLOSE;
            notify();
        }
        super.close();
    }

    private enum State
    {
        READ, WAIT, TIMEOUT, REMOTE_CLOSE, CLOSE
    }
}
