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

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @version $Revision$ $Date$
 */
public class StandardSchedulerPerformanceTest
{
    public static void main(String[] args) throws Exception
    {
        new StandardSchedulerPerformanceTest().testSchedulerPerformance();
    }

    @Test
    public void testSchedulerPerformance() throws Exception
    {
        int count = 5;
        int loops = 2;
        int sleep = 100;

        CountDownLatch latch = new CountDownLatch(count);
        Executor threadPool = new ThreadPoolExecutor(50, 250, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new CallerBlocksPolicy());
        Scheduler scheduler = new StandardScheduler();

        Scheduler.Task[] tasks = new Scheduler.Task[count];
        TaskWorker[] workers = new TaskWorker[count];
        for (int i = 0; i < count; ++i)
        {
            tasks[i] = scheduler.newTask(new Runnable()
            {
                public void run()
                {
                }
            });
            workers[i] = new TaskWorker(scheduler, tasks[i], latch, loops, sleep);
        }

        long start = System.nanoTime();
        for (int i = 0; i < count; ++i)
            threadPool.execute(workers[i]);

        assertTrue(latch.await(loops * (2 * sleep + 1), TimeUnit.MILLISECONDS));
        long end = System.nanoTime();
        System.out.println("elapsed = " + TimeUnit.NANOSECONDS.toMillis(end - start));
    }

    private class TaskWorker implements Runnable
    {
        private final Random random = new Random();
        private final Scheduler scheduler;
        private final Scheduler.Task task;
        private final CountDownLatch latch;
        private final int loops;
        private final int sleep;

        public TaskWorker(Scheduler scheduler, Scheduler.Task task, CountDownLatch latch, int loops, int sleep)
        {
            this.scheduler = scheduler;
            this.task = task;
            this.latch = latch;
            this.loops = loops;
            this.sleep = sleep;
        }

        public void run()
        {
            try
            {
                TimeUnit.MILLISECONDS.sleep(random.nextInt(sleep));
                for (int i = 0; i < loops; ++i)
                {
                    scheduler.schedule(task, 10, TimeUnit.SECONDS);
                    TimeUnit.MILLISECONDS.sleep(random.nextInt(sleep));
                    scheduler.cancel(task);
                    TimeUnit.MILLISECONDS.sleep(random.nextInt(sleep));
                }
            }
            catch (InterruptedException x)
            {
                // Ignored
            }
            finally
            {
                latch.countDown();
            }
        }
    }
}
