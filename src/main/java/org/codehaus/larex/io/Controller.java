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
 * @version $Revision$ $Date$
 */
public interface Controller
{
    /**
     * @param size the size of the read buffer
     */
    void setReadBufferSize(int size);

    void addInterceptor(Interceptor interceptor);

    boolean removeInterceptor(Interceptor interceptor);

    /**
     * <p>Asks the I/O system to register (if {@code needsRead} is true) or
     * to deregister (if {@code needsRead} is false) for interest in read events.</p>
     * <p>Normally, a connection will call this method when it detects that
     * a request is not complete and more data needs to be read.</p>
     *
     * @param needsRead true to indicate that there is interest in receiving read events,
     *                  false to indicate that there is no interest in receiving read events.
     */
    void needsRead(boolean needsRead);

    /**
     * <p>Asks the I/O system to register (if {@code needsWrite} is true) or
     * to deregister (if {@code needsWrite} is false) for interest in write events.</p>
     * <p>Normally, a channel will call this method when it detects that it cannot
     * write more bytes to the I/O system.</p>
     *
     * @param needsWrite true to indicate that there is interest in receiving read events,
     *                   false to indicate that there is no interest in receiving read events.
     */
    void needsWrite(boolean needsWrite);

    /**
     * <p>Non-blocking writes bytes from the given {@code buffer}.</p>
     * <p>Normally, a connection will call this method after it filled the buffer with bytes
     * to write, and this coordinator will forward the call to the channel.</p>
     *
     * @param buffer the buffer to write bytes from
     * @return the number of bytes written
     * @throws RuntimeSocketClosedException if the associated channel has been closed
     * @see Channel#write(ByteBuffer)
     */
    int write(ByteBuffer buffer) throws RuntimeSocketClosedException;

    /**
     * <p>Closes the given stream type of the channel associated with this coordinator.</p>
     *
     * @param type the stream type to close
     * @see Channel#close(StreamType)
     */
    void close(StreamType type);

    /**
     * <p>Closes the channel associated with this coordinator.</p>
     *
     * @see Channel#close()
     */
    void close();
}
