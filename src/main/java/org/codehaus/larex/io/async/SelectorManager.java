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

/**
 * <p>{@link SelectorManager} hides the complexity of working with {@link java.nio.channels.Selector}.</p>
 * <p>A {@link SelectorManager} associates an {@link AsyncEndpoint} to a {@link Listener} so that
 * when the I/O system associated to the endpoint signals readiness for I/O events, the listener is
 * notified. This normally results in the listener to call the endpoint to perform the actual I/O.</p>
 *
 * @version $Revision: 903 $ $Date$
 */
public interface SelectorManager
{
    public void register(AsyncEndpoint endpoint, Listener listener);

    public void update(AsyncEndpoint endpoint, int operations, boolean add);

    public void wakeup();

    public void close();

    public boolean awaitClosed(long timeout) throws InterruptedException;

    /**
     * <p>The interface for receiving events from the {@link SelectorManager}.</p>
     */
    public interface Listener
    {
        /**
         * <p>Invoked when the {@link SelectorManager} detects that the I/O system is ready to read.</p>
         * @see #writeReady()
         */
        public void readReady();

        /**
         * <p>Invoked when the {@link SelectorManager} detects that the I/O system is ready to write.</p>
         * @see #readReady()
         */
        public void writeReady();

        /**
         * <p>Invoked when the {@link SelectorManager} detects that the I/O system is closed.</p>
         */
        void close();
    }
}
