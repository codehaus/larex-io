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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision$ $Date$
 */
public abstract class ClosableConnection implements Connection
{
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final boolean debug = logger.isDebugEnabled();
    private final Coordinator coordinator;
    private volatile CountDownLatch softClose;

    protected ClosableConnection(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    protected Coordinator getCoordinator()
    {
        return coordinator;
    }

    public void onRemoteClose()
    {
    }

    public void onClose()
    {
    }

    public final void onClosed()
    {
        doOnClosed();
        onClosedHook();
    }

    void doOnClosed()
    {
        CountDownLatch softClose = this.softClose;
        if (softClose != null)
            softClose.countDown();
    }

    protected void onClosedHook()
    {
    }

    /**
     * <p>Half-closes this connection.</p>
     * @param type the half to close on this connection
     */
    public final void close(CloseType type)
    {
        doClose();
        coordinator.close(type);
    }

    void doClose()
    {
    }

    public final void close()
    {
        doClose();
        coordinator.close();
    }

    public final boolean softClose(long timeout) throws InterruptedException
    {
        CountDownLatch softClose = new CountDownLatch(1);
        this.softClose = softClose;
        close(CloseType.OUTPUT);
        boolean result = softClose.await(timeout, TimeUnit.MILLISECONDS);
        this.softClose = null;
        if (!result)
            close();
        return result;
    }
}
