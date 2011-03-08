/*
 * Copyright (c) 2010 the original author or authors
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>Concrete implementation of {@link Connection} that provides non-blocking read functionality,
 * non-blocking write functionality and inherits close functionalities.</p>
 * <p>User code normally extends this class to provide its own logic in
 * {@link #onRead(ByteBuffer)} which, in this class, does nothing.</p>
 */
public class StandardConnection extends ClosableConnection
{
    private final CountDownLatch opened = new CountDownLatch(1);
    private final NonBlockingWriter writer;

    public StandardConnection(Controller controller)
    {
        super(controller);
        this.writer = new NonBlockingWriter(controller);
    }

    @Override
    void postOpen()
    {
        super.postOpen();
        getController().needsRead(true);
        opened.countDown();
    }

    @Override
    void postWrite()
    {
        super.postWrite();
        writer.writeReadyEvent();
    }

    @Override
    void postWriteTimeout()
    {
        super.postWriteTimeout();
        writer.writeTimeoutEvent();
    }

    @Override
    void postClosing(StreamType type)
    {
        super.postClosing(type);
        if (type == StreamType.OUTPUT || type == StreamType.INPUT_OUTPUT)
            writer.closingEvent();
    }

    /**
     * <p>Awaits the given {@code timeout} (in milliseconds) for this connection
     * to be opened.</p>
     * @param timeout the maximum time to wait for this connection to be opened
     * @return whether the connection opened within the given {@code timeout}
     * @throws InterruptedException if the thread waiting for the connection to
     * open is interrupted by another thread
     */
    public boolean awaitOpened(long timeout) throws InterruptedException
    {
        return opened.await(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * <p>Writes the bytes in the given buffer with non-blocking semantic.</p>
     * <p>The given buffer will be fully consumed, either because it has been
     * fully written or because the remaining bytes have been copied into a
     * temporary buffer to be written later; the buffer is therefore disposable
     * upon return from this method.</p>
     *
     * @param buffer the buffer to write
     * @return the number of bytes of the given buffer that have been written
     */
    public final int write(ByteBuffer buffer)
    {
        return writer.write(buffer);
    }

    /**
     * The factory that creates instances of {@link StandardConnection}.
     */
    public static class Factory implements ConnectionFactory<StandardConnection>
    {
        @Override
        public StandardConnection newConnection(Controller controller)
        {
            return new StandardConnection(controller);
        }
    }
}
