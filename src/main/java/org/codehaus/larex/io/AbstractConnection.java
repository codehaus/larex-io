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

import java.nio.ByteBuffer;

/**
 * <p>Partial implementation of {@link Connection} that defines
 * an API that subclasses override to implement specific behavior.</p>
 */
public class AbstractConnection implements Connection
{
    public final void openEvent()
    {
        onOpen();
        postOpen();
    }

    /**
     * <p>Callback method invoked when the connection is opened</p>
     */
    protected void onOpen()
    {
    }

    void postOpen()
    {
    }

    public final boolean readEvent(ByteBuffer buffer)
    {
        return onRead(buffer);
    }

    /**
     * <p>Callback method invoked when data has been read from the remote
     * peer into the given {@code buffer}.</p>
     *
     * @param buffer the buffer containing the bytes read
     * @return whether to set read interest
     */
    protected boolean onRead(ByteBuffer buffer)
    {
        return true;
    }

    public final void readTimeoutEvent()
    {
        onReadTimeout();
        postReadTimeout();
    }

    /**
     * <p>Callback method invoked when the read times out.</p>
     */
    protected void onReadTimeout()
    {
    }

    void postReadTimeout()
    {
    }

    public final void writeEvent()
    {
        onWrite();
        postWrite();
    }

    /**
     * <p>Callback method invoked when the underlying connection is ready
     * to write again.</p>
     */
    protected void onWrite()
    {
    }

    void postWrite()
    {
    }

    public final void writeTimeoutEvent()
    {
        onWriteTimeout();
        postWriteTimeout();
    }

    /**
     * <p>Callback method invoked when the write times out.</p>
     */
    protected void onWriteTimeout()
    {
    }

    void postWriteTimeout()
    {
    }

    public final void remoteCloseEvent()
    {
        onRemoteClose();
        postRemoteClose();
    }

    /**
     * <p>Callback method invoked when it is detected that the remote peer
     * has closed the underlying connection.</p>
     */
    protected void onRemoteClose()
    {
    }

    void postRemoteClose()
    {
    }

    public final void closingEvent(StreamType type)
    {
        onClosing(type);
        postClosing(type);
    }

    /**
     * <p>Callback method invoked invoked just before the underlying connection
     * is being closed locally.</p>
     *
     * @param type the stream type that is about to be closed
     */
    protected void onClosing(StreamType type)
    {
    }

    void postClosing(StreamType type)
    {
    }

    public final void closedEvent(StreamType type)
    {
        onClosed(type);
        postClosed(type);
    }

    /**
     * <p>Callback method invoked just after the underlying connection
     * is closed locally.</p>
     *
     * @param type the stream type that has been closed
     */
    protected void onClosed(StreamType type)
    {
    }

    void postClosed(StreamType type)
    {
    }
}
