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
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @version $Revision$ $Date$
 */
public class StandardSchedulerTest
{
    private StandardScheduler scheduler;

    @Before
    public void init()
    {
        scheduler = new StandardScheduler();
    }

    @After
    public void destroy() throws Exception
    {
        scheduler.shutdown();
        scheduler.join(1000);
    }

    @Test
    public void testScheduleExecute() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        Scheduler.Task task = scheduler.newTask(new Runnable()
        {
            public void run()
            {
                latch.countDown();
            }
        });

        long delay = TimeUnit.MILLISECONDS.toNanos(50);
        long start = System.nanoTime();
        scheduler.schedule(task, 10 * delay, TimeUnit.NANOSECONDS);
        assertTrue(latch.await(15 * delay, TimeUnit.NANOSECONDS));
        long end = System.nanoTime();
        long elapsed = end - start;
        assertTrue(elapsed >= 10 * delay);
        assertTrue(elapsed <= 11 * delay);

        // Give time to the scheduler to set the status of the Task
        Thread.sleep(100);
        // TODO: perhaps it will be nice to have a Task.join() method that waits, like Future.get()

        assertFalse(task.isCancelled());
        assertTrue(task.isCompleted());
    }

    @Test
    public void testScheduleCancel() throws Exception
    {
        final AtomicBoolean executed = new AtomicBoolean(false);
        Scheduler.Task task = new StandardScheduler.StandardTask(null)
        {
            @Override
            protected boolean process()
            {
                executed.set(true);
                return super.process();
            }
        };

        long delay = TimeUnit.MILLISECONDS.toNanos(50);
        scheduler.schedule(task, 10 * delay, TimeUnit.NANOSECONDS);

        // Wait a while
        TimeUnit.NANOSECONDS.sleep(5 * delay);

        // Cancel the task
        assertTrue(scheduler.cancel(task));

        // Wait another while
        TimeUnit.NANOSECONDS.sleep(10 * delay);

        assertFalse(executed.get());
        assertTrue(task.isCancelled());
        assertFalse(task.isCompleted());
    }

    @Test
    public void testScheduleCancelReschedule() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        Scheduler.Task task = scheduler.newTask(new Runnable()
        {
            public void run()
            {
                latch.countDown();
            }
        });

        long delay = TimeUnit.MILLISECONDS.toNanos(50);
        scheduler.schedule(task, 10 * delay, TimeUnit.NANOSECONDS);

        // Wait a while
        TimeUnit.NANOSECONDS.sleep(5 * delay);

        // Cancel the task
        assertTrue(scheduler.cancel(task));

        // Reschedule
        long start = System.nanoTime();
        scheduler.schedule(task, 10 * delay, TimeUnit.NANOSECONDS);
        assertTrue(latch.await(15 * delay, TimeUnit.NANOSECONDS));
        long end = System.nanoTime();
        long elapsed = end - start;
        assertTrue(elapsed >= 10 * delay);
        assertTrue(elapsed <= 11 * delay);

        // Wait another while
        TimeUnit.NANOSECONDS.sleep(10 * delay);

        assertFalse(task.isCancelled());
        assertTrue(task.isCompleted());
    }

    @Test
    public void testScheduleCancelExecute() throws Exception
    {

    }
}
