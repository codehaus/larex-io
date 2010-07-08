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

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadWriteSelector implements Selector
{
    private static final AtomicInteger ids = new AtomicInteger();

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();
    private volatile java.nio.channels.Selector selector;
    private volatile Thread thread;
    private volatile boolean needsWakeup;

    public void open()
    {
        try
        {
            this.selector = java.nio.channels.Selector.open();
            this.thread = newSelectorThread(new SelectorLoop());
            this.thread.start();
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    protected Thread newSelectorThread(Runnable selector)
    {
        return new Thread(selector, "Selector-" + ids.incrementAndGet());
    }

    public void register(Channel channel, Listener listener)
    {
        channel.register(selector, listener);
    }

    public void update(Channel channel, int operations, boolean add)
    {
        // It is quite important, performance wise, that the operations
        // update is performed without creating a task, and reducing
        // the selector wake-ups at minimum.
        // Updating the selection key operations does not by itself
        // automatically wake up the selector.
        // Removing operations interest is normally done in the selector
        // thread and *before* the selector waits again on a select() call.
        // This ensures that the selector has an updated status for the
        // selection key.
        // Adding operations interest, on the other hand, when not done
        // from the selector thread, needs to wake up the selector so
        // that it can call select() and notice that the selection key
        // status has changed.

        channel.update(operations, add);
    }

    public void unregister(Channel channel, Listener listener)
    {
        channel.unregister(selector, listener);
    }

    public void submit(Runnable task)
    {
        if (Thread.currentThread() != thread)
        {
            tasks.add(task);
            logger.debug("Added task {}", task);
            if (needsWakeup)
                wakeup();
        }
        else
        {
            runTasks();
            runTask(task);
            logger.debug("Avoided wakeup for task {}", task);
        }
    }

    protected void wakeup()
    {
        selector.wakeup();
    }

    public void close()
    {
        submit(new Close());
    }

    public boolean join(long timeout) throws InterruptedException
    {
        thread.join(timeout);
        return !thread.isAlive();
    }

    protected void selectLoop()
    {
        boolean debug = logger.isDebugEnabled();
        while (selector.isOpen())
        {
            try
            {
                processTasks();

                if (debug)
                    logger.debug("Selector loop waiting on select");
                int selected = select();
                if (debug)
                    logger.debug("Selector loop woken up from select, {}/{} selected", selected, selector.keys().size());

                needsWakeup = false;

                // Closing the selector causes a wakeup, check if we have to exit
                if (!selector.isOpen())
                    break;

                // The select() may be woken up by selection key updates (for example
                // from NONE to READ interest), but most of the times the number of
                // keys selected will be zero.
                // Therefore we select again without blocking so that the selector
                // will notice that the selection key was updated.
                // This gives an good performance boost (benchmark with and without
                // to believe it).
                if (selected == 0)
                {
                    selected = selectNow();
                    if (debug)
                        logger.debug("Selector loop re-selecting, {}/{} selected", selected, selector.keys().size());
                }

                if (selected > 0)
                {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    for (Iterator<SelectionKey> iterator = selectedKeys.iterator(); iterator.hasNext();)
                    {
                        SelectionKey selectedKey = iterator.next();
                        if (debug)
                            logger.debug("Selector loop selected key {} with operations {}", selectedKey, selectedKey.interestOps());
                        iterator.remove();

                        if (!selectedKey.isValid())
                        {
                            if (debug)
                                logger.debug("Selector loop ignoring invalid key {}", selectedKey);
                            continue;
                        }

                        processKey(selectedKey);
                    }
                }
            }
            catch (ClosedSelectorException x)
            {
                break;
            }
            catch (IOException x)
            {
                close();
                throw new RuntimeIOException(x);
            }
        }
    }

    protected int select() throws IOException
    {
        return selector.select();
    }

    protected int selectNow() throws IOException
    {
        return selector.selectNow();
    }

    protected void processTasks()
    {
        runTasks();

        // If tasks are submitted here, they read will not wakeup the selector
        // Therefore below we run again the tasks

        needsWakeup = true;

        // Run again the tasks to avoid the race condition where a task is
        // submitted but will not wake up the selector
        runTasks();
    }

    private void runTasks()
    {
        Runnable task;
        while ((task = tasks.poll()) != null)
        {
            logger.debug("Processing task {}", task);
            runTask(task);
        }
    }

    protected void runTask(Runnable task)
    {
        task.run();
    }

    protected void processKey(SelectionKey selectedKey) throws IOException
    {
        try
        {
            if (selectedKey.isReadable())
            {
                Listener listener = (Listener)selectedKey.attachment();
                listener.onReadReady();
            }
            if (selectedKey.isWritable())
            {
                Listener listener = (Listener)selectedKey.attachment();
                listener.onWriteReady();
            }
        }
        catch (CancelledKeyException x)
        {
            logger.info("Ignoring cancelled key {}", selectedKey);
        }
    }

    private class Close implements Runnable
    {
        public void run()
        {
            for (SelectionKey key : selector.keys())
            {
                Listener listener = (Listener)key.attachment();
                listener.onClose();
            }

            try
            {
                selector.close();
            }
            catch (IOException x)
            {
                throw new RuntimeIOException(x);
            }
        }
    }

    private class SelectorLoop implements Runnable
    {
        public void run()
        {
            logger.debug("Selector loop entered");
            try
            {
                selectLoop();
            }
            finally
            {
                logger.debug("Selector loop exited");
            }
        }
    }
}
