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

package org.codehaus.larex.io.ssl;

import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import org.codehaus.larex.io.BlockingFlusher;
import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.Interceptor;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.RuntimeSocketClosedException;
import org.codehaus.larex.io.StreamType;

/**
 * @version $Revision$ $Date$
 */
public class SSLInterceptor extends Interceptor.Forwarder
{
    private final ByteBuffers sslByteBuffers;
    private final SSLEngine sslEngine;
    private final Controller controller;
    private final SSLFlusher flusher;
    private volatile boolean handshaking = true;
    private volatile ByteBuffer sslBuffer;

    public SSLInterceptor(ByteBuffers sslByteBuffers, SSLEngine sslEngine, Controller controller)
    {
        this.sslByteBuffers = sslByteBuffers;
        this.sslEngine = sslEngine;
        this.controller = controller;
        this.flusher = new SSLFlusher(controller);
    }

    @Override
    public void onOpen()
    {
        try
        {
            // Signal to the SSL engine that we're starting the handshake
            sslEngine.beginHandshake();
            SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
            out: while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED)
            {
                logger.debug("Handshake status: {}", handshakeStatus);
                switch (handshakeStatus)
                {
                    case NEED_WRAP:
                    {
                        handshakeWrap();
                        break;
                    }
                    case NEED_TASK:
                    {
                        executeTasks();
                        break;
                    }
                    case NEED_UNWRAP:
                    {
                        // We are done with sending, read response
                        return;
                    }
                    default:
                        throw new IllegalStateException();
                }
                handshakeStatus = sslEngine.getHandshakeStatus();
            }
        }
        catch (SSLException x)
        {
            controller.close(StreamType.INPUT_OUTPUT);
            throw new RuntimeIOException(x);
        }
    }

    private void executeTasks()
    {
        Runnable task;
        while ((task = sslEngine.getDelegatedTask()) != null)
        {
            task.run();
            logger.debug("Executed task {}", task);
        }
    }

    @Override
    public boolean onRead(ByteBuffer sslBuffer)
    {
        logger.debug("Reading {}", sslBuffer);
        int bufferSize = sslEngine.getSession().getApplicationBufferSize();
        ByteBuffer buffer = sslByteBuffers.acquire(bufferSize, false);
        try
        {
            out: while (handshaking)
            {
                SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
                logger.debug("Handshake status: {}", handshakeStatus);
                switch (handshakeStatus)
                {
                    case NEED_UNWRAP:
                    {
                        if (handshakeUnwrap(sslBuffer, buffer))
                            break out;
                        break;
                    }
                    case NEED_TASK:
                    {
                        executeTasks();
                        break;
                    }
                    case NEED_WRAP:
                    {
                        handshakeWrap();
                        break out;
                    }
                    case NOT_HANDSHAKING:
                    {
                        handshaking = false;
                        // TLS handshake completed, now we are ready
                        super.onOpen();
                        break out;
                    }
                    default:
                        throw new IllegalStateException();
                }
            }

            if (handshaking)
                return true;

            return wrap(sslBuffer, buffer);
        }
        catch (SSLException x)
        {
            controller.close(StreamType.INPUT_OUTPUT);
            throw new RuntimeIOException(x);
        }
        finally
        {
            sslByteBuffers.release(buffer);
        }
    }

    private boolean wrap(ByteBuffer sslBuffer, ByteBuffer buffer) throws SSLException
    {
        boolean readMore = true;
        int bufferSize = sslEngine.getSession().getApplicationBufferSize();
        out: while (sslBuffer.hasRemaining())
        {
            ByteBuffer source = fillLocal(sslBuffer);

            logger.debug("Wrapping from {} to {}", source, buffer);
            SSLEngineResult result = sslEngine.unwrap(source, buffer);
            logger.debug("Wrapped from {} to {}, result {}", new Object[]{source, buffer, result});
            switch (result.getStatus())
            {
                case OK:
                {
                    // Prepare for read
                    buffer.flip();
                    // Forward the call with the unencrypted bytes
                    readMore = super.onRead(buffer);
                    // Cleanup the buffer
                    buffer.clear();
                    buffer.limit(bufferSize);
                    // Cleanup the SSL buffer
                    resetLocal();

                    // TODO: check this: I don't think we are behaving properly if readMore==false
                    if (readMore)
                        break;
                    else
                        break out;
                }
                case BUFFER_UNDERFLOW:
                {
                    prepareLocal(sslBuffer);
                    break out;
                }
                case CLOSED:
                {
                    handshakeWrap();
                    break out;
                }
                default:
                    throw new IllegalStateException();
            }
        }
        return readMore;
    }

    private void prepareLocal(ByteBuffer sslBuffer)
    {
        // Not enough data was read, need to copy and store what was read
        int sslBufferSize = sslEngine.getSession().getPacketBufferSize();
        ByteBuffer local = this.sslBuffer;
        if (local == null)
        {
            local = sslByteBuffers.acquire(sslBufferSize, false);
            local.put(sslBuffer);
            this.sslBuffer = local;
            logger.debug("Acquired local SSL buffer {}", local);
        }
        else
        {
            local.position(local.limit());
            local.limit(sslBufferSize);
        }
    }

    private void resetLocal()
    {
        ByteBuffer local = this.sslBuffer;
        if (local != null)
        {
            if (local.hasRemaining())
            {
                local.compact();
                local.limit(sslEngine.getSession().getPacketBufferSize());
                logger.debug("Compacted local SSL buffer {}", local);
            }
            else
            {
                logger.debug("Releasing local SSL buffer {}", local);
                sslByteBuffers.release(local);
                this.sslBuffer = null;
            }
        }
    }

    private ByteBuffer fillLocal(ByteBuffer sslBuffer)
    {
        // If we have a previous unfinished read, coalesce it
        ByteBuffer result = sslBuffer;
        ByteBuffer local = this.sslBuffer;
        if (local != null)
        {
            // Transfer all possible bytes from sslBuffer to this.sslBuffer without overflowing
            int remaining = Math.min(local.remaining(), sslBuffer.remaining());
            int limit = sslBuffer.limit();
            sslBuffer.limit(sslBuffer.position() + remaining);
            local.put(sslBuffer);
            sslBuffer.limit(limit);

            local.flip();
            result = local;
        }
        return result;
    }

    @Override
    public void onRemoteClose()
    {
        try
        {
            sslEngine.closeInbound();
            assert sslEngine.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
            super.onRemoteClose();
        }
        catch (SSLException x)
        {
            // Could be a truncation attack where a man in the middle
            // truncates the message then sends a FIN.
            // Do not signal to the application that the connection was
            // orderly closed by the remote peer.
            logger.debug("", x);
        }
    }

    @Override
    public void close(StreamType type)
    {
        // TODO: handle stream types

        ByteBuffer sslBuffer = sslByteBuffers.acquire(sslEngine.getSession().getPacketBufferSize(), false);
        try
        {
            sslEngine.closeOutbound();
            SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
            out: while (handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
            {
                switch (handshakeStatus)
                {
                    case NEED_WRAP:
                    {
                        handshakeWrap();
                        break;
                    }
                    case NEED_UNWRAP:
                    {
                        super.close(type);
                        break out;
                    }
                    default:
                        throw new IllegalStateException();
                }
                handshakeStatus = sslEngine.getHandshakeStatus();
            }
        }
        catch (SSLException x)
        {
            throw new RuntimeIOException(x);
        }
        finally
        {
            sslByteBuffers.release(sslBuffer);
        }
    }

    @Override
    public int write(ByteBuffer buffer)
    {
        int sslBufferSize = sslEngine.getSession().getPacketBufferSize();
        ByteBuffer sslBuffer = sslByteBuffers.acquire(sslBufferSize, false);
        try
        {
            SSLEngineResult result = sslEngine.wrap(buffer, sslBuffer);
            logger.debug("Wrapping result: {}", result);
            assert result.getStatus() == SSLEngineResult.Status.OK;
            sslBuffer.flip();
            int length = sslBuffer.remaining();
            flush(sslBuffer);
            return length;
        }
        catch (SSLException x)
        {
            controller.close(StreamType.INPUT_OUTPUT);
            throw new RuntimeIOException(x);
        }
        finally
        {
            sslByteBuffers.release(sslBuffer);
        }
    }

    private boolean handshakeUnwrap(ByteBuffer sslBuffer, ByteBuffer buffer) throws SSLException
    {
        logger.debug("Handshake reading from {}", sslBuffer);

        // If we have a previous unfinished read, coalesce it
        ByteBuffer source = fillLocal(sslBuffer);

        while (source.hasRemaining())
        {
            logger.debug("Handshake unwrapping from {} to {}", source, buffer);
            SSLEngineResult result = sslEngine.unwrap(source, buffer);
            logger.debug("Handshake unwrapped from {} to {}, result {}", new Object[]{source, buffer, result});
            switch (result.getStatus())
            {
                case OK:
                {
                    // Everything is unwrapped, we're done
                    resetLocal();
                    break;
                }
                case BUFFER_UNDERFLOW:
                {
                    prepareLocal(sslBuffer);
                    break;
                }
                case CLOSED:
                {
                    // We have unwrapped a close SSL message, just break
                    break;
                }
                default:
                    throw new IllegalStateException();
            }
            if (result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_UNWRAP)
                return false;
        }
        return true;
    }

    private void handshakeWrap() throws SSLException
    {
        int sslBufferSize = sslEngine.getSession().getPacketBufferSize();
        ByteBuffer sslBuffer = sslByteBuffers.acquire(sslBufferSize, false);
        try
        {
            // During handshake wrapping there is nothing to send
            ByteBuffer buffer = ByteBuffer.allocate(0);
            while (true)
            {
                sslBuffer.clear();
                sslBuffer.limit(sslBufferSize);
                logger.debug("Handshake wrapping to {}", sslBuffer);
                SSLEngineResult result = sslEngine.wrap(buffer, sslBuffer);
                logger.debug("Handshake wrapped to {}, result {}", sslBuffer, result);
                assert result.getStatus() == SSLEngineResult.Status.OK || result.getStatus() == SSLEngineResult.Status.CLOSED;
                sslBuffer.flip();
                try
                {
                    flush(sslBuffer);
                }
                catch (RuntimeSocketClosedException x)
                {
                    if (result.getStatus() != SSLEngineResult.Status.CLOSED)
                        throw x;
                }
                if (result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_WRAP)
                    break;
            }
        }
        finally
        {
            sslByteBuffers.release(sslBuffer);
        }
    }

    private void flush(ByteBuffer sslBuffer)
    {
        flusher.flush(sslBuffer);
    }

    @Override
    public void onWrite()
    {
        flusher.writeReadyEvent();
        super.onWrite();
    }

    @Override
    public void onWriteTimeout()
    {
        flusher.writeTimeoutEvent();
        super.onWriteTimeout();
    }

    @Override
    public void onClosing(StreamType type)
    {
        if (type == StreamType.OUTPUT || type == StreamType.INPUT_OUTPUT)
            flusher.closeEvent();
        super.onClosing(type);
    }

    private class SSLFlusher extends BlockingFlusher
    {
        private SSLFlusher(Controller controller)
        {
            super(controller);
        }

        @Override
        protected int write(ByteBuffer buffer)
        {
            return SSLInterceptor.super.write(buffer);
        }

        @Override
        protected void close()
        {
            SSLInterceptor.this.controller.close(StreamType.INPUT_OUTPUT);
        }
    }
}
