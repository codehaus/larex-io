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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * <p>A {@link RejectedExecutionHandler} policy that blocks the caller until the task
 * can be re-submitted to the thread pool.</p>
 */
public class CallerBlocksPolicy implements RejectedExecutionHandler
{
    public void rejectedExecution(Runnable task, ThreadPoolExecutor executor)
    {
        try
        {
            executor.getQueue().put(task);
        }
        catch (InterruptedException x)
        {
            throw new RejectedExecutionException(x);
        }
    }
}
