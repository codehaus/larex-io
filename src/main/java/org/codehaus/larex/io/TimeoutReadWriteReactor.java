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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @version $Revision$ $Date$
 */
public class TimeoutReadWriteReactor extends ReadWriteReactor implements Runnable
{
    private static final AtomicInteger ids = new AtomicInteger();

    private final ConcurrentMap<Coordinator, Boolean> coordinators = new ConcurrentHashMap<Coordinator, Boolean>();
    private volatile long timerPeriod = 1000;
    private volatile boolean active;
    private volatile Thread thread;

    public long getTimerPeriod()
    {
        return timerPeriod;
    }

    public void setTimerPeriod(long timerPeriod)
    {
        this.timerPeriod = timerPeriod;
    }

    @Override
    public void open()
    {
        super.open();
        thread = newTimerThread(this);
        active = true;
        thread.start();
    }

    protected Thread newTimerThread(Runnable timer)
    {
        Thread thread = new Thread(timer, getClass().getSimpleName() + "-" + ids.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void register(Channel channel, Listener listener)
    {
        super.register(channel, listener);
        if (listener instanceof Coordinator)
            coordinators.put((Coordinator)listener, Boolean.TRUE);
    }

    @Override
    public void unregister(Channel channel, Listener listener)
    {
        super.unregister(channel, listener);
        if (listener instanceof Coordinator)
            coordinators.remove(listener);
    }

    @Override
    public void close()
    {
        active = false;
        thread.interrupt();
        super.close();
    }

    @Override
    public boolean join(long timeout) throws InterruptedException
    {
        thread.join(timeout);
        return super.join(timeout) && !thread.isAlive();
    }

    public void run()
    {
        logger.debug("Timer loop entered, period {} ms", getTimerPeriod());
        try
        {
            while (active)
            {
                TimeUnit.MILLISECONDS.sleep(getTimerPeriod());

                Set<Coordinator> coordinators = this.coordinators.keySet();
                if (logger.isDebugEnabled())
                    logger.debug("Timer checking {} connections", coordinators.size());
                for (Coordinator coordinator : coordinators)
                {
                    coordinator.timeoutRead();
                    coordinator.timeoutWrite();
                }
            }
        }
        catch (InterruptedException x)
        {
            // Exiting
        }
        finally
        {
            logger.debug("Timer loop exited");
        }
    }
}
