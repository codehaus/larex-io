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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Revision$ $Date$
 */
public interface Interceptor
{
    Interceptor getNext();

    void setNext(Interceptor interceptor);

    void onPrepare();

    void onOpen();

    void onReadTimeout();

    boolean onRead(ByteBuffer buffer);

    void onWrite();

    void onWriteTimeout();

    int write(ByteBuffer buffer);

    void onRemoteClose();

    void onClosing(StreamType type);

    void onClosed(StreamType type);

    void close(StreamType type);

    public static class Forwarder implements Interceptor
    {
        protected final Logger logger = LoggerFactory.getLogger(getClass());
        private volatile Interceptor next;

        public Interceptor getNext()
        {
            return next;
        }

        public void setNext(Interceptor next)
        {
            this.next = next;
        }

        public void onPrepare()
        {
            getNext().onPrepare();
        }

        public void onOpen()
        {
            getNext().onOpen();
        }

        public void onReadTimeout()
        {
            getNext().onReadTimeout();
        }

        public boolean onRead(ByteBuffer buffer)
        {
            return getNext().onRead(buffer);
        }

        public void onWrite()
        {
            getNext().onWrite();
        }

        public void onWriteTimeout()
        {
            getNext().onWriteTimeout();
        }

        public int write(ByteBuffer buffer)
        {
            return getNext().write(buffer);
        }

        public void onRemoteClose()
        {
            getNext().onRemoteClose();
        }

        public void onClosing(StreamType type)
        {
            getNext().onClosing(type);
        }

        public void onClosed(StreamType type)
        {
            getNext().onClosed(type);
        }

        public void close(StreamType type)
        {
            getNext().close(type);
        }
    }
}
