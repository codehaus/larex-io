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
 * <p>{@link Connection} represents an active connection with a remote peer and it is the recipient
 * of events that are related to the activity happening on the connection.</p>
 * <p>These events involve {@link #openEvent() connection opening}, {@link #readEvent(ByteBuffer) read}
 * and {@link #writeEvent() write} events, close events ({@link #remoteCloseEvent() remote} and
 * {@link #closingEvent() local}) and timeout events.</p>
 * <p>User code would prefer to extend from {@link Connection} implementations such as
 * {@link StandardConnection} or {@link BlockingConnection}.</p>
 * <br />
 * <p><b>IMPLEMENTATION NOTES</b></p>
 * <p>Implementing directly a {@link Connection} requires controlling carefully threading to avoid
 * to block {@link Selector} threads or timer threads, and must be done in concert with the
 * {@link Coordinator} implementation.</p>
 *
 * @version $Revision: 903 $ $Date$
 */
public interface Connection
{
    /**
     * <p>Callback method called when this connection is opened.</p>
     */
    void openEvent();

    /**
     * <p>Callback method called after the connection has been opened.</p>
     */
    void readyEvent();

    /**
     * <p>Callback method called when this connection has read bytes sent from the remote peer.</p>
     * @param buffer the buffer containing the bytes read
     */
    void readEvent(ByteBuffer buffer);

    /**
     * <p>Callback method called when this connection times out while waiting to read bytes.<p>
     */
    void readTimeoutEvent();

    /**
     * <p>Callback method called when this connection is again ready to write, after having been
     * write blocked.</p>
     */
    void writeEvent();

    /**
     * <p>Callback method called when this connection times out while waiting to write bytes.<p>
     */
    void writeTimeoutEvent();

    /**
     * <p>Callback method called when this connection detects that the remote end has been closed.</p>
     */
    void remoteCloseEvent();

    /**
     * <p>Callback called when this connection is about to be closed.</p>
     */
    void closingEvent();

    /**
     * <p>Callback called when this connection has been closed.</p>
     */
    void closedEvent();
}
