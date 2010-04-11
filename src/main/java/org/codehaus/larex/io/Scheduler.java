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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * <p>A scheduler that can schedule {@link Task}s to be executed after a given delay.</p>
 * <p>This class provides functionalities similar to {@link ScheduledExecutorService}, but
 * allows to avoid the creation of a new {@link ScheduledFuture} for each scheduled task.<br />
 * This is hopefully better for tasks that are scheduled and immediately cancelled.</p>
 *
 * @version $Revision$ $Date$
 */
public interface Scheduler
{
    /**
     * <p>Wraps the given {@code command} into a Task.</p>
     *
     * @param command the command to wrap
     * @return a task wrapping the given command
     */
    public Task newTask(Runnable command);

    /**
     * <p>Schedules the given {@code task} to be executed after the given {@code delay}.</p>
     *
     * @param task the task to execute
     * @param delay the delay (after now) at which the tasks is to be executed
     * @param unit the unit of time of the delay parameter
     */
    public void schedule(Task task, long delay, TimeUnit unit);

    /**
     * <p>Attempts to cancel the execution of the given {@code task}.</p>
     * @param task the task to cancel
     * @return true if the tasks could be cancelled, false otherwise
     */
    public boolean cancel(Task task);

    /**
     * <p>Shuts down this scheduler.</p>
     *
     * @see #shutdown()
     */
    public void shutdown();

    /**
     * <p>Blocks after a shutdown request until this scheduler terminates, the give {@code timeout}
     * elapses or the current thread is interrupted.</p>
     *
     * @param timeout the maximum time to wait, in milliseconds
     * @return true if this scheduler terminated, false if the timeout elapsed
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean join(long timeout) throws InterruptedException;

    /**
     * A wrapper for commands to be scheduled by {@link Scheduler}.
     */
    public interface Task
    {
        /**
         * @return whether this task has been cancelled
         */
        public boolean isCancelled();

        /**
         * @return whether this task has been executed
         */
        public boolean isCompleted();
    }
}
