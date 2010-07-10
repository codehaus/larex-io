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

    protected void onOpen()
    {
    }

    public final boolean readEvent(ByteBuffer buffer)
    {
        doOnRead(buffer);
        return onRead(buffer);
    }

    void doOnRead(ByteBuffer buffer)
    {
    }

    protected boolean onRead(ByteBuffer buffer)
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

    protected void onClosed(StreamType type)
    {
    }
}
