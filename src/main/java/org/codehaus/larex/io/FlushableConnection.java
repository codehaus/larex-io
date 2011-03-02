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

/**
 * <p>Partial implementation of {@link Connection}, that provides flush functionalities
 * and inherits close functionalities.</p>
 * <p>Writes can be non-blocking (via {@link #write(ByteBuffer)}), or blocking (via
 * {@link #flush(ByteBuffer)}.</p>
 */
public abstract class FlushableConnection extends ClosableConnection
{
    private final BlockingWriter writer;

    protected FlushableConnection(Controller controller)
    {
        super(controller);
        this.writer = new BlockingWriter(controller);
    }

    @Override
    void doOnWrite()
    {
        super.doOnWrite();
        writer.writeReadyEvent();
    }

    @Override
    void doOnWriteTimeout()
    {
        super.doOnWriteTimeout();
        writer.writeTimeoutEvent();
    }

    @Override
    void doOnClosing(StreamType type)
    {
        super.doOnClosing(type);
        if (type == StreamType.OUTPUT || type == StreamType.INPUT_OUTPUT)
            writer.closeEvent();
    }

    /**
     * <p>Blocking-writes the bytes contained in the given buffer.</p>
     * <p>This call is blocking and will only return when all the bytes have been written,
     * the write timeout expires, or the connection is closed.</p>
     *
     * @param buffer the buffer to write
     * @throws RuntimeSocketTimeoutException if the write timeout expires
     * @throws RuntimeSocketClosedException  if the connection is closed
     */
    public final void flush(ByteBuffer buffer) throws RuntimeSocketTimeoutException, RuntimeSocketClosedException
    {
        writer.flush(buffer);
    }

    /**
     * <p>Non-blocking write the bytes contained in the given buffer.</p>
     *
     * @param buffer the buffer to flush
     * @return the bytes written
     */
    public final int write(ByteBuffer buffer)
    {
        return getController().write(buffer);
    }
}
