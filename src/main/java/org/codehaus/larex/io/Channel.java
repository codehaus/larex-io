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
import java.nio.channels.SelectableChannel;

/**
 * <p>{@link Channel} hides the complexity of working with {@link SelectableChannel}s.</p>
 *
 * @version $Revision: 903 $ $Date$
 */
public interface Channel
{
    /**
     * <p>Registers this channel with the given {@code selector} for read interest
     * and with the given {@code listener} as attachment.</p>
     *
     * @param selector the selector this channel must register with
     * @param listener the attachment to the registration
     * @throws RuntimeSocketClosedException if this channel has been closed
     * @see SelectableChannel#register(java.nio.channels.Selector , int, Object)
     */
    public void register(java.nio.channels.Selector selector, Selector.Listener listener) throws RuntimeSocketClosedException;

    /**
     * <p>Updates this channel's interests, adding (or removing) the given {@code operations}.</p>
     *
     * @param operations the interest operations to add or remove
     * @param add        whether to add or remove the operations
     * @throws RuntimeSocketClosedException if this channel selection key has been canceled
     */
    public void update(int operations, boolean add) throws RuntimeSocketClosedException;

    /**
     * <p>Reads bytes from this channel into the given buffer.</p>
     * <p>The number of bytes read can be determined using the buffer, and it may not be
     * the case that if this method returns true, then no bytes have been read.</p>
     *
     * @param buffer the buffer to read into
     * @return true if the remote end was closed, false otherwise
     * @throws RuntimeSocketClosedException if this channel has been closed
     */
    public boolean read(ByteBuffer buffer) throws RuntimeSocketClosedException;

    /**
     * <p>Non-blocking writes the bytes in the given buffer to this channel.</p>
     *
     * @param buffer the buffer to write from
     * @return the number of written bytes
     * @throws RuntimeSocketClosedException if this channel has been closed
     */
    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException;

    /**
     * @param type the stream type to test for close
     * @return whether this channel is closed for the given stream type
     * @see #close(ChannelStreamType)
     * @see #isClosed()
     */
    public boolean isClosed(ChannelStreamType type);

    /**
     * <p>Closes the given stream type of this channel.</p>
     *
     * @param type the stream type to close
     * @see #isClosed(ChannelStreamType)
     */
    public void close(ChannelStreamType type);

    /**
     * @return whether this channel is closed
     * @see #close()
     * @see #isClosed(ChannelStreamType)
     */
    public boolean isClosed();

    /**
     * <p>Closes this channel.</p>
     *
     * @see #isClosed()
     */
    public void close();
}
