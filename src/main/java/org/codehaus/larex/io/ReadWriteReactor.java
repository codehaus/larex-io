/*
 * Copyright (c) 2010 the original author or authors
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
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReadWriteReactor implements Reactor
{
    private static final AtomicInteger ids = new AtomicInteger();

    protected final Logger logger = LoggerFactory.getLogger(Reactor.class);
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<Runnable>();
    private volatile Selector selector;
    private volatile Thread thread;
    private volatile boolean needsWakeup = true;

    public void open()
    {
        try
        {
            this.selector = Selector.open();
            this.thread = newReactorThread(new ReactorLoop());
            this.thread.start();
        }
        catch (IOException x)
        {
            throw new RuntimeIOException(x);
        }
    }

    protected Thread newReactorThread(Runnable reactor)
    {
        return new Thread(reactor, "Reactor-" + ids.incrementAndGet());
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
        // Removing operations interest is normally done in the reactor
        // thread and *before* the selector waits again on a select() call.
        // This ensures that the selector has an updated status for the
        // selection key.
        // Adding operations interest, on the other hand, when not done
        // from the reactor thread, needs to wake up the selector so
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
        boolean debug = logger.isDebugEnabled();
        if (Thread.currentThread() != thread)
        {
            tasks.add(task);
            if (debug)
                logger.debug("Added task {}", task);
            boolean wakeup = needsWakeup;
            if (wakeup)
                wakeup();
        }
        else
        {
            runTasks();
            runTask(task);
            if (debug)
                logger.debug("Run task {}", task);
        }
    }

    protected void wakeup()
    {
        selector.wakeup();
    }

    public void close()
    {
        Close task = new Close();
        submit(task);
        task.await();
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
                    logger.debug("Reactor loop waiting on select");
                int selected = select();
                if (debug)
                    logger.debug("Reactor loop woken up from select, {}/{} selected", selected, selector.keys().size());

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
                // TODO: check if this optimization is still needed. Previously we were updating the interest ops from
                // TODO: non-reactor threads, so it made sense to call selectNow() since the key was updated already
                // TODO: but this proved to grab a lock with the selector, becoming a bottleneck, so now interest ops
                // TODO: are updated with a task; if so, then we should not need this optimization
                if (selected == 0 && tasks.isEmpty())
                {
                    selected = selectNow();
                    if (debug)
                        logger.debug("Reactor loop re-selecting, {}/{} selected", selected, selector.keys().size());

                    // If the selector has notified of an operation interest, and the operation interest
                    // has not been reset, and the operation has not been performed (for example a read),
                    // the selector will continue to wake up with selected == 0, but with a non-empty
                    // selected key set. Assert that this condition does not happen
                    assert selector.selectedKeys().size() == 0;
                }

                if (selected > 0)
                {
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    for (Iterator<SelectionKey> iterator = selectedKeys.iterator(); iterator.hasNext();)
                    {
                        SelectionKey selectedKey = iterator.next();
                        if (debug)
                            logger.debug("Reactor loop selected key {} with operations {}", selectedKey, selectedKey.interestOps());
                        iterator.remove();

                        if (!selectedKey.isValid())
                        {
                            if (debug)
                                logger.debug("Reactor loop ignoring invalid key {}", selectedKey);
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

        // If tasks are submitted between these 2 statements, they will not
        // wakeup the selector, therefore below we run again the tasks

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
            int readyOps = selectedKey.readyOps();
            if ((readyOps & SelectionKey.OP_READ) != 0)
            {
                Listener listener = (Listener)selectedKey.attachment();
                listener.onReadReady();
            }
            if ((readyOps & SelectionKey.OP_WRITE) != 0)
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
        private final CountDownLatch latch = new CountDownLatch(1);

        public void run()
        {
            try
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
            finally
            {
                latch.countDown();
            }
        }

        public void await()
        {
            try
            {
                latch.await();
            }
            catch (InterruptedException x)
            {
                Thread.currentThread().interrupt();
                throw new RuntimeIOException(x);
            }
        }
    }

    private class ReactorLoop implements Runnable
    {
        public void run()
        {
            logger.debug("Reactor loop entered");
            try
            {
                selectLoop();
            }
            finally
            {
                logger.debug("Reactor loop exited");
            }
        }
    }
}
