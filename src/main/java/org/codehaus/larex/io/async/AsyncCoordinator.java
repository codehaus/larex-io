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

package org.codehaus.larex.io.async;

import java.nio.ByteBuffer;

import org.codehaus.larex.io.RuntimeSocketClosedException;

/**
 * <p>{@link AsyncCoordinator} coordinates the activity between the {@link AsyncChannel},
 * the {@link AsyncInterpreter} and the {@link AsyncSelector}.</p>
 * <p>{@link AsyncCoordinator} receives I/O events from the {@link AsyncSelector}, and
 * dispatches them appropriately to either the {@link AsyncChannel} or the {@link AsyncInterpreter}.</p>
 * <p/>
 *
 * @version $Revision: 903 $ $Date$
 */
public interface AsyncCoordinator extends AsyncSelector.Listener
{
    /**
     * @param channel the channel associated with this coordinator
     */
    public void setAsyncChannel(AsyncChannel channel);

    /**
     * @param interpreter the interpreter associated with this coordinator
     */
    public void setAsyncInterpreter(AsyncInterpreter interpreter);

    /**
     * @param size the size of the read buffer
     */
    public void setReadBufferSize(int size);

    /**
     * <p>Asks the I/O system to register (if {@code needsRead} is true) or
     * to deregister (if {@code needsRead} is false) for interest in read events.</p>
     * <p>Normally, an interpreter will call this method when it detects that
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
     *                  false to indicate that there is no interest in receiving read events.
     */
    public void needsWrite(boolean needsWrite);

    /**
     * <p>Reads bytes from the given {@code buffer}.</p>
     * <p>Normally, a channel will call this method after it read bytes from the I/O system,
     * and this coordinator will forward the method call to the interpreter.</p>
     *
     * @param buffer the buffer to read bytes from
     * @see AsyncInterpreter#onRead(ByteBuffer)
     */
    public void onRead(ByteBuffer buffer);

    /**
     * <p>Writes bytes from the given {@code buffer}.</p>
     * <p>Normally, an interpreter will call this method after it filled the buffer with bytes
     * to write, and this coordinator will forward the call to the channel.</p>
     * @param buffer the buffer to write bytes from
     * @throws RuntimeSocketClosedException if the associated channel has been closed
     * @see AsyncChannel#write(ByteBuffer)
     */
    public void write(ByteBuffer buffer) throws RuntimeSocketClosedException;

    /**
     * <p>Closes this coordinator and the associated channel.</p>
     * @see AsyncChannel#close()
     */
    public void close();

    public void onClose();
}
