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

/**
 * @version $Revision$ $Date$
 */
public abstract class BlockingConnection extends WritableConnection
{
    private ByteBuffer buffer;
    private ReadState readState = ReadState.WAIT;

    public BlockingConnection(Coordinator coordinator)
    {
        super(coordinator);
    }

    public final void onOpen()
    {
    }

    public void onRead(ByteBuffer buffer)
    {
        synchronized (this)
        {
            this.buffer.put(buffer);
            readState = ReadState.READ;
            notify();
        }
    }

    public void onReadTimeout()
    {
        synchronized (this)
        {
            readState = ReadState.TIMEOUT;
            notify();
        }
    }

    protected int read(ByteBuffer buffer)
    {
        int start = buffer.position();
        getCoordinator().setReadBufferSize(buffer.remaining());

        synchronized (this)
        {
            if (readState == ReadState.REMOTE_CLOSE)
                return -1;

            this.buffer = buffer;
            readState = ReadState.WAIT;
            getCoordinator().needsRead(true);
            while (readState == ReadState.WAIT)
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
                if (readState == ReadState.TIMEOUT)
                    throw new RuntimeSocketTimeoutException();
                else if (readState == ReadState.CLOSE)
                    throw new RuntimeSocketClosedException();
                else if (readState == ReadState.REMOTE_CLOSE)
                    return -1;
            }
        }

        return buffer.position() - start;
    }

    public final void onRemoteClose()
    {
        synchronized (this)
        {
            readState = ReadState.REMOTE_CLOSE;
            notify();
        }
        onRemoteCloseHook();
    }

    protected void onRemoteCloseHook()
    {
    }

    @Override
    void doClose()
    {
        synchronized (this)
        {
            readState = ReadState.CLOSE;
            notify();
        }
        super.doClose();
    }

    private enum ReadState
    {
        READ, WAIT, TIMEOUT, REMOTE_CLOSE, CLOSE
    }
}
