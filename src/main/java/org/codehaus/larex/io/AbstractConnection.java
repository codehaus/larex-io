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

import java.nio.ByteBuffer;

/**
 * <p>Partial implementation of {@link Connection} that defines
 * an API that subclasses override to implement specific behavior.</p>
 */
public class AbstractConnection implements Connection
{
    public final void openEvent()
    {
        doOnOpen();
        onOpen();
    }

    void doOnOpen()
    {
    }

    /**
     * <p>Callback method invoked when the connection is opened</p>
     */
    protected void onOpen()
    {
    }

    public final void readEvent(ByteBuffer buffer)
    {
        doOnRead(buffer);
        onRead(buffer);
    }

    void doOnRead(ByteBuffer buffer)
    {
    }

    /**
     * <p>Callback method invoked when data has been read from the remote
     * peer into the given {@code buffer}.</p>
     *
     * @param buffer the buffer containing the bytes read
     * @see #onReadEnd()
     */
    protected void onRead(ByteBuffer buffer)
    {
    }

    @Override
    public final boolean readEndEvent()
    {
        doOnReadEnd();
        return onReadEnd();
    }

    void doOnReadEnd()
    {
    }

    /**
     * <p>Callback method invoked when there is no more data to read from the
     * time being.</p>
     * <p>This method must return whether the framework must set read interest
     * to receive further read events.</p>
     * <p>By default, this method returns true.</p>
     *
     * @return whether to set read interest
     * @see #onRead(ByteBuffer)
     */
    protected boolean onReadEnd()
    {
        return true;
    }

    public final void readTimeoutEvent()
    {
        doOnReadTimeout();
        onReadTimeout();
    }

    void doOnReadTimeout()
    {
    }

    /**
     * <p>Callback method invoked when the read times out.</p>
     */
    protected void onReadTimeout()
    {
    }

    public final void writeEvent()
    {
        doOnWrite();
        onWrite();
    }

    void doOnWrite()
    {
    }

    /**
     * <p>Callback method invoked when the underlying connection is ready
     * to write again.</p>
     */
    protected void onWrite()
    {
    }

    public final void writeTimeoutEvent()
    {
        doOnWriteTimeout();
        onWriteTimeout();
    }

    void doOnWriteTimeout()
    {
    }

    /**
     * <p>Callback method invoked when the write times out.</p>
     */
    protected void onWriteTimeout()
    {
    }

    public final void remoteCloseEvent()
    {
        doOnRemoteClose();
        onRemoteClose();
    }

    void doOnRemoteClose()
    {
    }

    /**
     * <p>Callback method invoked when it is detected that the remote peer
     * has closed the underlying connection.</p>
     */
    protected void onRemoteClose()
    {
    }

    public final void closingEvent(StreamType type)
    {
        doOnClosing(type);
        onClosing(type);
    }

    void doOnClosing(StreamType type)
    {
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

    public final void closedEvent(StreamType type)
    {
        doOnClosed(type);
        onClosed(type);
    }

    void doOnClosed(StreamType type)
    {
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
}
