/*
 * Copyright (c) 2011 the original author or authors.
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

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class StandardConnectionTest
{
    @Test
    public void testAwaitOpened() throws Exception
    {
        final StandardConnection connection = new StandardConnection.Factory().newConnection(new EmptyController());

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                connection.openEvent();
            }
        }.start();

        long timeout = sleep * 2;
        long start = System.nanoTime();
        assertTrue(connection.awaitOpened(timeout));
        long end = System.nanoTime();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(end - start) >= sleep);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(end - start) < timeout);
    }

    private void sleepFor(long time)
    {
        try
        {
            TimeUnit.MILLISECONDS.sleep(time);
        }
        catch (InterruptedException x)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeSocketTimeoutException(x);
        }
    }
}
