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
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;

/**
 * <p>A {@link Channel} wraps a {@link SelectableChannel}, offering a simpler
 * programming interface to work with.</p>
 * <p>{@link Channel}s are registered with a {@link Selector}, and differently
 * from the {@code java.nio} API they can be also unregistered, providing symmetry
 * to the API.<br />
 * Registration and unregistration are performed in the proper thread transparently
 * to avoid lockups with the {@link Reactor} thread.</p>
 * <p> {@link Channel}s input and output streams may be closed independently or
 * together.</p>
 */
public interface Channel
{
    /**
     * <p>Registers this channel with the given {@code selector}
     * and with the given {@code listener} as attachment.</p>
     *
     * @param selector the selector this channel must register with
     * @param listener the attachment of the registration
     * @return whether the registration has been successful
     * @throws RuntimeSocketClosedException if this channel has been closed
     * @see java.nio.channels.SelectableChannel#register(Selector, int, Object)
     * @see #unregister(Selector, Reactor.Listener)
     */
    public boolean register(Selector selector, Reactor.Listener listener) throws RuntimeSocketClosedException;

    /**
     * <p>Updates this channel's interests, adding (or removing) the given {@code operations}.</p>
     *
     * @param operations the interest operations to add or remove
     * @param add        whether to add or remove the operations
     * @throws RuntimeSocketClosedException if this channel selection key has been canceled
     */
    public void update(int operations, boolean add) throws RuntimeSocketClosedException;

    /**
     * <p>Unregisters this channel from the given {@code selector}.</p>
     *
     * @param selector the selector this channel was registered with
     * @param listener the attachment of the registration
     * @return whether the unregistration has been successful
     * @see #register(Selector, Reactor.Listener)
     */
    public boolean unregister(Selector selector, Reactor.Listener listener);

    /**
     * <p>Reads bytes from this channel into the given buffer.</p>
     * <p>The number of bytes read can be determined using the buffer's position
     * before and after the read.<br/>
     * This method returns true if the underlying channel has been closed, but
     * it may be possible that before the close bytes have been read into the
     * given {@code buffer}.</p>
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
     * @see #close(StreamType)
     */
    public boolean isClosed(StreamType type);

    /**
     * <p>Closes the given stream type of this channel.</p>
     *
     * @param type the stream type to close
     * @see #isClosed(StreamType)
     */
    public void close(StreamType type);
}
