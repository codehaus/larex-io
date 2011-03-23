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
 * <p>Intercepts read, write and close events to allow transformation
 * of incoming/outgoing data.</p>
 * <p>Incoming data flows into the interceptor before arriving to the {@link Connection}.</p>
 * <p>Outgoing data flows from the connection to the interceptor to the {@link Controller}.</p>
 * <p>{@link Interceptor}s are added to a controller via
 * {@link Controller#addInterceptor(Interceptor)} and form a chain that is processed
 * in the same order they have been added to the controller.</p>
 */
public interface Interceptor
{
    /**
     * @return the next interceptor in the chain
     */
    public Interceptor getNext();

    /**
     * @param interceptor the next interceptor in the chain
     */
    public void setNext(Interceptor interceptor);

    /**
     * <p>Callback method invoked when the connection is opened,
     * before {@link Connection#openEvent()}.</p>
     */
    public void onOpen();

    /**
     * <p>Callback method invoked when the connection has read bytes,
     * before {@link Connection#readEvent(ByteBuffer)}.</p>
     *
     * @param buffer the buffer with the bytes read
     * @return whether to receive further read events
     */
    public boolean onRead(ByteBuffer buffer);

    /**
     * <p>Callback method invoked when the read timed out, before
     * {@link Connection#readTimeoutEvent()}.</p>
     */
    public void onReadTimeout();

    /**
     * <p>Callback method invoked when the controller writes data,
     * after {@link Controller#write(ByteBuffer)}.</p>
     *
     * @param buffer the buffer to write
     * @return the number of bytes written
     */
    public int write(ByteBuffer buffer);

    /**
     * <p>Callback method invoked when the underlying connection is
     * ready to be written, before {@link Connection#writeEvent()}.</p>
     */
    public void onWrite();

    /**
     * <p>Callback method invoked when the write timed out, before
     * {@link Connection#writeTimeoutEvent()}.</p>
     */
    public void onWriteTimeout();

    /**
     * <p>Callback method invoked when the remote peer has closed its
     * side of the underlying connection, before {@link Connection#remoteCloseEvent()}.</p>
     */
    public void onRemoteClose();

    /**
     * <p>Callback method invoked just before the controller is closing
     * the underlying connection's stream type, before {@link Connection#closingEvent(StreamType)}.</p>
     *
     * @param type the stream type that is being closed
     */
    public void onClosing(StreamType type);

    /**
     * <p>Callback method invoked when the controller is closing the underlying
     * connection's stream type, before {@link Controller#close(StreamType)}.</p>
     *
     * @param type the stream type to close
     */
    public void close(StreamType type);

    /**
     * <p>Callback method invoked just after the controller has closed its
     * side of the underlying connection, before {@link Connection#closedEvent(StreamType)}.</p>
     *
     * @param type the stream type that was closed
     */
    public void onClosed(StreamType type);

    /**
     * <p>Implementation of {@link Interceptor} that forwards the events to the next interceptor in the chain.</p>
     */
    public static class Forwarder implements Interceptor
    {
        private volatile Interceptor next;

        @Override
        public Interceptor getNext()
        {
            return next;
        }

        @Override
        public void setNext(Interceptor next)
        {
            this.next = next;
        }

        @Override
        public void onOpen()
        {
            getNext().onOpen();
        }

        @Override
        public void onReadTimeout()
        {
            getNext().onReadTimeout();
        }

        @Override
        public boolean onRead(ByteBuffer buffer)
        {
            return getNext().onRead(buffer);
        }

        @Override
        public void onWrite()
        {
            getNext().onWrite();
        }

        @Override
        public void onWriteTimeout()
        {
            getNext().onWriteTimeout();
        }

        @Override
        public int write(ByteBuffer buffer)
        {
            return getNext().write(buffer);
        }

        @Override
        public void onRemoteClose()
        {
            getNext().onRemoteClose();
        }

        @Override
        public void onClosing(StreamType type)
        {
            getNext().onClosing(type);
        }

        @Override
        public void onClosed(StreamType type)
        {
            getNext().onClosed(type);
        }

        @Override
        public void close(StreamType type)
        {
            getNext().close(type);
        }
    }
}
