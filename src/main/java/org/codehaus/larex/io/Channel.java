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
     * @param selector   the selector this channel must register with
     * @param listener   the attachment to the registration
     * @throws RuntimeSocketClosedException if this channel has been closed
     * @see SelectableChannel#register(java.nio.channels.Selector , int, Object)
     */
    public void register(java.nio.channels.Selector selector, Selector.Listener listener) throws RuntimeSocketClosedException;

    /**
     * <p>Updates the channel's interests, adding (or removing) the given {@code operations}.</p>
     * @param operations the interest operations to add or remove
     * @param add whether to add or remove the operations
     * @throws RuntimeSocketClosedException if this channel selection key has been canceled
     */
    public void update(int operations, boolean add) throws RuntimeSocketClosedException;

    public void read(int readBufferSize) throws RuntimeSocketClosedException;

    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException;

    public void close();
}
