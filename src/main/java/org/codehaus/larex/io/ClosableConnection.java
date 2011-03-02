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

import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>Partial implementation of {@link Connection} that provides close functionalities.</p>
 * <p>Closing a connection can be done in two ways: hard closing the connection, or soft
 * closing the connection.</p>
 * <p>When a connection is {@link #close() hard closed}, the socket associated with the connection
 * is closed (via {@link SocketChannel#close()}).</p>
 * <p>When a connection is {@link #softClose(long) soft closed}, only its output is shut down (via
 * {@link Socket#shutdownOutput()}), but not its input; the remote end of the connection
 * detects this ({@link #remoteCloseEvent()} is invoked, or equivalently a raw socket would read -1),
 * and may optionally decide to write the last data before hard closing the connection.<br />
 * The connection reads the last data sent by the remote end, then detects that the remote end
 * was closed and can now hard close the connection.</p>
 * <p>Note however that the remote end cannot know if the connection was soft closed or hard closed,
 * so it must be prepared to failures when writing the last data.</p>
 */
public abstract class ClosableConnection extends AbstractConnection
{
    private final Controller controller;
    private volatile CountDownLatch softClose;

    protected ClosableConnection(Controller controller)
    {
        this.controller = controller;
    }

    /**
     * @return the controller associated with this connection
     */
    protected Controller getController()
    {
        return controller;
    }

    @Override
    void doOnClosed(StreamType type)
    {
        super.doOnClosed(type);
        if (type == StreamType.INPUT_OUTPUT)
        {
            CountDownLatch softClose = this.softClose;
            if (softClose != null)
                softClose.countDown();
        }
    }

    /**
     * <p>Closes the given stream type of this connection.</p>
     *
     * @param type the stream type to close
     */
    public final void close(StreamType type)
    {
        getController().close(type);
    }

    /**
     * <p>Hard closes this connection.</p>
     *
     * @see #softClose(long)
     */
    public final void close()
    {
        close(StreamType.INPUT_OUTPUT);
    }

    /**
     * <p>Soft closes this connection, waiting the specified timeout for the remote end
     * to collaborate in closing the connection.<br />
     * If the remote end does not close the connection within the timeout, this connection
     * is then {@link #close() hard closed}.</p>
     *
     * @param timeout the timeout to wait, in milliseconds, for the remote end to close the connection
     * @return true if the connection was soft closed, false if it was hard closed
     * @see #close()
     */
    public final boolean softClose(long timeout)
    {
        CountDownLatch softClose = new CountDownLatch(1);
        this.softClose = softClose;
        close(StreamType.OUTPUT);
        boolean result = awaitSoftClose(softClose, timeout);
        this.softClose = null;
        if (!result)
            close();
        return result;
    }

    private boolean awaitSoftClose(CountDownLatch softClose, long timeout)
    {
        try
        {
            return softClose.await(timeout, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException x)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
