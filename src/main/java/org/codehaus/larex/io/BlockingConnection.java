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
 * <p>Implementation of {@link Connection} that provides blocking read functionality,
 * blocking write functionalities and inherits close functionalities.</p>
 * <p>User code must implement {@link #onOpen()}, typically in the following way:</p>
 * <pre>
 * public void onOpen()
 * {
 *     ByteBuffer readBuffer = ByteBuffer.allocate(256);
 *     int read = read(readBuffer);
 *     // Do something with bytes just read
 *
 *     ByteBuffer writeBuffer = ByteBuffer.allocate(256);
 *     // Fill the writeBuffer with response data
 *     write(writeBuffer);
 * }
 * </pre>
 */
public class BlockingConnection extends ClosableConnection
{
    private final BlockingReader reader;
    private final BlockingWriter writer;

    public BlockingConnection(Controller controller)
    {
        super(controller);
        this.reader = new BlockingReader(controller);
        this.writer = new BlockingWriter(controller);
    }

    /**
     * <p>Overridden to implement the blocking read functionality.</p>
     *
     * @param buffer the buffer containing the bytes to read
     */
    @Override
    protected final boolean onRead(ByteBuffer buffer)
    {
        return reader.fill(buffer);
    }

    /**
     * <p>Blocking reads bytes into the given buffer.</p>
     *
     * @param buffer the buffer to read bytes into
     * @return -1 if the remote end has been closed, or the number of bytes that has been read
     * @throws RuntimeSocketClosedException  if this connection has been closed
     * @throws RuntimeSocketTimeoutException if the read timed out
     */
    protected int read(ByteBuffer buffer) throws RuntimeSocketClosedException, RuntimeSocketTimeoutException
    {
        return reader.read(buffer);
    }

    @Override
    void postWrite()
    {
        super.postWrite();
        writer.writeReadyEvent();
    }

    void postReadTimeout()
    {
        super.postReadTimeout();
        reader.readTimeoutEvent();
    }

    @Override
    void postWriteTimeout()
    {
        super.postWriteTimeout();
        writer.writeTimeoutEvent();
    }

    /**
     * <p>Overridden to implement the blocking read functionality.</p>
     */
    void postRemoteClose()
    {
        super.postRemoteClose();
        reader.remoteCloseEvent();
    }

    @Override
    void postClosing(StreamType type)
    {
        super.postClosing(type);
        if (type == StreamType.INPUT || type == StreamType.INPUT_OUTPUT)
            reader.closeEvent();
        if (type == StreamType.OUTPUT || type == StreamType.INPUT_OUTPUT)
            writer.closeEvent();
    }

    public int available()
    {
        return reader.available();
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
    public final void write(ByteBuffer buffer) throws RuntimeSocketTimeoutException, RuntimeSocketClosedException
    {
        writer.write(buffer);
    }
}
