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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public class DispatchCoordinator extends TimeoutCoordinator
{
    private final Runnable onOpenAction = new OnOpenAction();
    private final Runnable onReadAction = new OnReadAction();
    private final Runnable onWriteAction = new OnWriteAction();
    private final Runnable onCloseAction = new OnCloseAction();
    private final Runnable onReadTimeoutAction = new ReadTimeoutAction();
    private final Runnable onWriteTimeoutAction = new WriteTimeoutAction();
    private final Executor threadPool;

    public DispatchCoordinator(Reactor reactor, ByteBuffers byteBuffers, Executor threadPool, long readTimeout, long writeTimeout)
    {
        super(reactor, byteBuffers, readTimeout, writeTimeout);
        this.threadPool = threadPool;
    }

    protected Executor getThreadPool()
    {
        return threadPool;
    }

    protected void dispatch(Runnable action)
    {
        try
        {
            getThreadPool().execute(action);
        }
        catch (RejectedExecutionException x)
        {
            logger.debug("", x);
        }
    }

    @Override
    public void onOpen()
    {
        dispatch(onOpenAction);
    }

    @Override
    public void onReadReady()
    {
        // Remove interest in further reads, otherwise the select loop will
        // continue to notify us that it is ready to read
        needsRead(false);
        // Dispatch the read to another thread
        dispatch(onReadAction);
    }

    @Override
    public void onWriteReady()
    {
        // Remove interest in further writes, otherwise the select loop will
        // continue to notify us that it is ready to write
        needsWrite(false);
        // Notify the suspended thread that it can write some more
        dispatch(onWriteAction);
    }

    @Override
    public void onClose()
    {
        dispatch(onCloseAction);
    }

    @Override
    protected void onReadTimeout()
    {
        dispatch(onReadTimeoutAction);
    }

    @Override
    protected void onWriteTimeout()
    {
        dispatch(onWriteTimeoutAction);
    }

    private class OnOpenAction implements Runnable
    {
        @Override
        public void run()
        {
            processOnOpen();
        }
    }

    private class OnReadAction implements Runnable
    {
        @Override
        public void run()
        {
            processOnRead();
        }
    }

    private class OnWriteAction implements Runnable
    {
        @Override
        public void run()
        {
            processOnWrite();
        }
    }

    private class OnCloseAction implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                processOnClose();
            }
            catch (Exception x)
            {
                logger.info("Could not process close", x);
            }
        }
    }

    private class ReadTimeoutAction implements Runnable
    {
        @Override
        public void run()
        {
            processOnReadTimeout();
        }
    }

    private class WriteTimeoutAction implements Runnable
    {
        @Override
        public void run()
        {
            processOnWriteTimeout();
        }
    }
}
