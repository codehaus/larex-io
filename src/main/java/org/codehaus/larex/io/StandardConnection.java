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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @version $Revision$ $Date$
 */
public class StandardConnection extends FlushableConnection
{
    private final CountDownLatch ready = new CountDownLatch(1);

    public StandardConnection(Coordinator coordinator)
    {
        super(coordinator);
    }

    void doOnOpen()
    {
        getCoordinator().needsRead(true);
    }

    @Override
    void doOnReady()
    {
        super.doOnReady();
        ready.countDown();
    }

    public boolean awaitReady(long timeout) throws InterruptedException
    {
        return ready.await(timeout, TimeUnit.MILLISECONDS);
    }

    public static class Factory implements ConnectionFactory<StandardConnection>
    {
        public StandardConnection newConnection(Coordinator coordinator)
        {
            return new StandardConnection(coordinator);
        }
    }
}
