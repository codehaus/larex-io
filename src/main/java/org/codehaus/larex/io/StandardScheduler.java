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

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision$ $Date$
 */
public class StandardScheduler implements Scheduler, Runnable
{
    private static final Logger logger = LoggerFactory.getLogger(StandardScheduler.class);
    private static final AtomicInteger ids = new AtomicInteger();
    private final DelayQueue<StandardTask> tasks = new DelayQueue<StandardTask>();
    private final Thread thread;

    public StandardScheduler()
    {
        this(new ThreadFactory()
        {
            public Thread newThread(Runnable r)
            {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName(StandardScheduler.class.getSimpleName() + "-" + ids.incrementAndGet());
                return thread;
            }
        });
    }

    public StandardScheduler(ThreadFactory threadFactory)
    {
        thread = threadFactory.newThread(this);
        thread.start();
    }

    public StandardTask newTask(Runnable command)
    {
        return new StandardTask(command);
    }

    public void schedule(Task task, long delay, TimeUnit unit)
    {
        StandardTask t = (StandardTask)task;
        boolean reset = t.reset();
        assert reset;
        boolean scheduled = t.schedule(System.nanoTime() + unit.toNanos(delay));
        assert scheduled;

        // TODO: if shutting down, reject
        tasks.offer(t);
        if (logger.isDebugEnabled())
            logger.debug("Scheduled task {}, queue size {}", t, tasks.size());
    }

    public boolean cancel(Task task)
    {
        StandardTask t = (StandardTask)task;
        if (t.cancel())
        {
            tasks.remove(t);
            if (logger.isDebugEnabled())
                logger.debug("Cancelled task {}, queue size {}", t, tasks.size());
            return true;
        }
        return false;
    }

    public void run()
    {
        logger.debug("Scheduler {} loop entered", this);
        while (true)
        {
            try
            {
                StandardTask task = tasks.take();
                if (logger.isDebugEnabled())
                    logger.debug("Scheduler {} took task {}", this, task);

                if (task.isCancelled())
                    continue;

                task.process();
            }
            catch (InterruptedException x)
            {
                break;
            }
        }
        logger.debug("Scheduler {} loop exited", this);
    }

    public void shutdown()
    {
        thread.interrupt();
    }

    public boolean join(long timeout) throws InterruptedException
    {
        // TODO: throw ISE if not shutdown
        thread.join(timeout);
        return thread.isAlive();
    }

    protected static class StandardTask implements Task, Delayed
    {
        private final static Logger logger = LoggerFactory.getLogger(StandardTask.class);
        private final AtomicReference<State> state = new AtomicReference<State>(State.UNSCHEDULED);
        private final Runnable command;
        private volatile long time;

        protected StandardTask(Runnable command)
        {
            this.command = command;
        }

        public long getDelay(TimeUnit unit)
        {
            return unit.convert(time - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        public int compareTo(Delayed other)
        {
            if (this == other)
                return 0;
            long thisTime = getDelay(TimeUnit.NANOSECONDS);
            long otherTime = other.getDelay(TimeUnit.NANOSECONDS);
            if (thisTime < otherTime)
                return -1;
            else if (thisTime > otherTime)
                return 1;
            return 0;
        }

        private boolean cancel()
        {
            return update(State.SCHEDULED, State.CANCELLED);
        }

        public boolean isCancelled()
        {
            return state.get() == State.CANCELLED;
        }

        public boolean isCompleted()
        {
            return state.get() == State.COMPLETED;
        }

        private boolean reset()
        {
            State oldState = state.get();
            boolean result = canBeReset(oldState);
            if (result)
                result = update(oldState, State.UNSCHEDULED);
            return result;
        }

        private boolean canBeReset(State state)
        {
            return state == State.CANCELLED || state == State.COMPLETED || state == State.UNSCHEDULED;
        }

        private boolean schedule(long time)
        {
            if (update(State.UNSCHEDULED, State.SCHEDULED))
            {
                this.time = time;
                return true;
            }
            return false;
        }

        protected boolean process()
        {
            boolean process = update(State.SCHEDULED, State.RUNNING);
            if (process)
            {
                try
                {
                    if (command != null)
                        command.run();
                }
                catch (RuntimeException x)
                {
                    logger.info("Exception while executing task " + this, x);
                }
                finally
                {
                    boolean completed = update(State.RUNNING, State.COMPLETED);
                    assert completed;
                }
            }
            return process;
        }

        private boolean update(State oldState, State newState)
        {
            if (logger.isDebugEnabled())
                logger.debug("Updating state for task {}: {} => {}", new Object[]{this, oldState, newState});
            return state.compareAndSet(oldState, newState);
        }

        @Override
        public String toString()
        {
            StringBuilder buffer = new StringBuilder(getClass().getSimpleName());
            buffer.append("@").append(System.identityHashCode(this));
            buffer.append("[").append(state.get()).append("]");
            return buffer.toString();
        }

        /**
         * unscheduled --> scheduled
         * scheduled   --> running, cancelled
         * running     --> completed
         * cancelled   --> unscheduled
         * completed   --> unscheduled
         */
        private enum State
        {
            SCHEDULED, RUNNING, COMPLETED, CANCELLED, UNSCHEDULED
        }
    }
}
