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
 * <p>{@link Controller} provides the API for user code to operate on the underlying connection
 * with the remote peer.</p>
 * <p>Where a {@link Connection} is passive and receives I/O events, {@link Controller} is
 * active and allows to control read/write interest, allows to {@link #write(ByteBuffer) write}
 * and to {@link #close(StreamType) close} the underlying connection with the remote peer.</p>
 * <p>It also provides user code the ability to add and remove {@link Interceptor}s, which
 * offer fine grained control on the activity happening on the connection.</p>
 */
public interface Controller
{
    /**
     * @param size the size of the read buffer
     */
    public void setReadBufferSize(int size);

    /**
     * <p>Appends the given {@code interceptor} to the queue of interceptors.</p>
     *
     * @param interceptor the interceptor to add
     */
    public void addInterceptor(Interceptor interceptor);

    /**
     * <p>Removes the given {@code interceptor} from the queue of interceptors.</p>
     *
     * @param interceptor the interceptor to remove
     * @return true if the interceptor was removed, false otherwise
     */
    public boolean removeInterceptor(Interceptor interceptor);

    /**
     * <p>Asks the I/O system to register (if {@code needsRead} is true) or
     * to deregister (if {@code needsRead} is false) for interest in read events.</p>
     * <p>Normally, a connection will call this method when it detects that
     * a request is not complete and more data needs to be read.</p>
     *
     * @param needsRead true to indicate that there is interest in receiving read events,
     *                  false to indicate that there is no interest in receiving read events.
     */
    public void needsRead(boolean needsRead);

    /**
     * <p>Asks the I/O system to register (if {@code needsWrite} is true) or
     * to deregister (if {@code needsWrite} is false) for interest in write events.</p>
     * <p>Normally, a channel will call this method when it detects that it cannot
     * write more bytes to the I/O system.</p>
     *
     * @param needsWrite true to indicate that there is interest in receiving read events,
     *                   false to indicate that there is no interest in receiving read events.
     */
    public void needsWrite(boolean needsWrite);

    /**
     * <p>Non-blocking writes bytes from the given {@code buffer}.</p>
     * <p>Normally, a connection will call this method after it filled the buffer with bytes
     * to write, and this controller will forward the call to the {@link Channel channel}.</p>
     *
     * @param buffer the buffer to write bytes from
     * @return the number of bytes written
     * @throws RuntimeSocketClosedException if the associated channel has been closed
     * @see Channel#write(ByteBuffer)
     */
    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException;

    /**
     * <p>Closes the given stream type of the channel associated with this controller.</p>
     *
     * @param type the stream type to close
     * @see Channel#close(StreamType)
     */
    public void close(StreamType type);

    /**
     * @param type the stream type to test for close
     * @return whether the associated channel is closed for the given stream type
     * @see #close(StreamType)
     */
    public boolean isClosed(StreamType type);
}
