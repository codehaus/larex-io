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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>Concrete implementation of {@link Connection} that inherits close and
 * blocking flush functionalities.</p>
 * <p>User code normally extends this class to provide its own logic in
 * {@link #onRead(ByteBuffer)} which, in this class, does nothing.</p>
 */
public class StandardConnection extends FlushableConnection
{
    private final CountDownLatch ready = new CountDownLatch(1);

    public StandardConnection(Controller controller)
    {
        super(controller);
    }

    @Override
    void doOnOpen()
    {
        super.doOnOpen();
        ready.countDown();
    }

    /**
     * <p>Awaits the given {@code timeout} (in milliseconds) for this connection
     * to be opened.</p>
     * @param timeout the maximum time to wait for this connection to be opened
     * @return whether the connection opened within the given {@code timeout}
     * @throws InterruptedException if the thread waiting for the connection to
     * open is interrupted by another thread
     */
    public boolean awaitOpened(long timeout) throws InterruptedException
    {
        return ready.await(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * The factory that creates instances of {@link StandardConnection}.
     */
    public static class Factory implements ConnectionFactory<StandardConnection>
    {
        @Override
        public StandardConnection newConnection(Controller controller)
        {
            return new StandardConnection(controller);
        }
    }
}
