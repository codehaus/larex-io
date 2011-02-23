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
 * <p>{@link Connection} represents a connection with a remote peer and it is the recipient
 * of events that are related to the activity happening on the connection.</p>
 * <p>These events involve {@link #openEvent() connection opening}, {@link #readEvent(ByteBuffer) read}
 * and {@link #writeEvent() write} events, close events ({@link #remoteCloseEvent() remote} and
 * {@link #closingEvent(StreamType) local}) and timeout events.</p>
 * <p>While {@link Connection} is the passive half and receives events from the underlying connection,
 * {@link Controller} is the active half and allows to operate on the underlying connection.
 * <p>User code would prefer to extend from {@code Connection} implementations such as
 * {@link StandardConnection} or {@link BlockingConnection}.</p>
 */
public interface Connection
{
    /**
     * <p>Callback method invoked after the connection has been opened.</p>
     */
    public void openEvent();

    /**
     * <p>Callback method invoked when this connection has read bytes sent from the remote peer.</p>
     * <p>This method may be called multiple times, for example because the read buffer is smaller
     * than the data available.</p>
     * <p>When the data available has been completely read for the time being, {@link #readEndEvent()}
     * will be called.</p>
     *
     * @param buffer the buffer containing the bytes read
     * @see #readEndEvent()
     */
    public void readEvent(ByteBuffer buffer);

    /**
     * <p>Callback method invoked when this connection has finished to read bytes from the remote
     * peer.</p>
     * <p>This method is invoked after {@link #readEvent(ByteBuffer)} when the data available has
     * been completely read for the time being. </p>
     *
     * @return whether to set read interest to receive further read events
     * @see #readEvent(ByteBuffer)
     */
    public boolean readEndEvent();

    /**
     * <p>Callback method invoked when this connection times out while waiting to read bytes.<p>
     */
    public void readTimeoutEvent();

    /**
     * <p>Callback method invoked when this connection is again ready to write, after having been
     * write blocked.</p>
     */
    public void writeEvent();

    /**
     * <p>Callback method invoked when this connection times out while waiting to write bytes.<p>
     */
    public void writeTimeoutEvent();

    /**
     * <p>Callback method invoked when this connection detects that the remote end has been closed.</p>
     */
    public void remoteCloseEvent();

    /**
     * <p>Callback method invoked when this connection is about to be closed.</p>
     *
     * @param type the stream type that is about to close
     */
    public void closingEvent(StreamType type);

    /**
     * <p>Callback method invoked when this connection has been closed.</p>
     *
     * @param type the stream type that has been closed
     */
    public void closedEvent(StreamType type);
}
