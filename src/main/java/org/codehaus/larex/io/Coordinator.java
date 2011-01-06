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

/**
 * <p>{@link Coordinator} coordinates the activity between the {@link Channel},
 * the {@link Connection} and the {@link Reactor}.</p>
 * <p>{@link Coordinator} receives I/O events from the {@link Reactor}, and
 * dispatches them appropriately to either the {@link Channel} or the {@link Connection}.</p>
 *
 * @version $Revision: 903 $ $Date$
 */
public interface Coordinator extends Reactor.Listener, Controller
{
    /**
     * @param channel the channel associated with this coordinator
     */
    public void setChannel(Channel channel);

    /**
     * @param connection the connection associated with this coordinator
     */
    public void setConnection(Connection connection);

    /**
     * <p>Invoked to check if the read timeout has elapsed, and if so, fire the appropriate event.</p>
     * @see Interceptor#onReadTimeout()
     */
    public void timeoutRead();

    /**
     * <p>Invoked to check if the write timeout has elapsed, and if so, fire the appropriate event.</p>
     * @see Interceptor#onWriteTimeout()
     */
    public void timeoutWrite();
}
