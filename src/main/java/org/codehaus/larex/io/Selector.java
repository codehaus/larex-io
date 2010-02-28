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
 * <p>{@link Selector} hides the complexity of working with {@link java.nio.channels.Selector}.</p>
 * <p>A {@link Selector} associates an {@link Channel} to a {@link Listener} so that
 * when the I/O system associated to the channel signals readiness for I/O events, the listener is
 * notified. This normally results in the listener to call the channel to perform the actual I/O.</p>
 *
 * @version $Revision: 903 $ $Date$
 */
public interface Selector
{
    public void register(Channel channel, Listener listener);

    public void update(Channel channel, int operations, boolean add);

    public void close();

    public void wakeup();

    public boolean join(long timeout) throws InterruptedException;

    /**
     * <p>The interface for receiving events from the {@link Selector}.</p>
     */
    public interface Listener
    {
        /**
         * <p>Invoked when the {@link Selector} first registers with the I/O system.</p>
         */
        void onOpen();

        /**
         * <p>Invoked when the {@link Selector} detects that the I/O system is ready to read.</p>
         * @see #onWriteReady()
         */
        public void onReadReady();

        /**
         * <p>Invoked when the {@link Selector} detects that the I/O system is ready to write.</p>
         * @see #onReadReady()
         */
        public void onWriteReady();

        /**
         * <p>Invoked when the {@link Selector} detects that the I/O system is closed.</p>
         */
        void onClose();
    }
}
