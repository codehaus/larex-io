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

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.ChannelStreamType;
import org.codehaus.larex.io.Coordinator;
import org.codehaus.larex.io.Flusher;
import org.codehaus.larex.io.Interceptor;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.RuntimeSocketClosedException;

/**
 * @version $Revision$ $Date$
 */
public class SSLInterceptor extends Interceptor.Forwarder
{
    private final ByteBuffers sslByteBuffers;
    private final SSLEngine sslEngine;
    private final SSLFlusher flusher;
    private volatile boolean handshaking = true;

    public SSLInterceptor(ByteBuffers sslByteBuffers, SSLEngine sslEngine, Coordinator coordinator)
    {
        this.sslByteBuffers = sslByteBuffers;
        this.sslEngine = sslEngine;
        this.flusher = new SSLFlusher(coordinator);
    }

    @Override
    public void onReady()
    {
        ByteBuffer sslBuffer = sslByteBuffers.acquire(sslEngine.getSession().getPacketBufferSize(), false);
        try
        {
            sslEngine.beginHandshake();
            SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
            out: while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED)
            {
                switch (handshakeStatus)
                {
                    case NEED_WRAP:
                    {
                        handshakeWrap(sslBuffer);
                        break;
                    }
                    case NEED_TASK:
                    {
                        executeTasks();
                        break;
                    }
                    case NEED_UNWRAP:
                        break out;
                    default:
                        throw new IllegalStateException();
                }
                handshakeStatus = sslEngine.getHandshakeStatus();
            }
        }
        catch (SSLException x)
        {
            close();
            throw new RuntimeIOException(x);
        }
        finally
        {
            sslByteBuffers.release(sslBuffer);
        }
    }

    private void executeTasks()
    {
        Runnable task;
        while ((task = sslEngine.getDelegatedTask()) != null)
            task.run();
    }

    @Override
    public void onRead(ByteBuffer sslBuffer)
    {
        ByteBuffer buffer = sslByteBuffers.acquire(sslEngine.getSession().getApplicationBufferSize(), false);
        try
        {
            out: while (handshaking)
            {
                SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
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
                        handshakeWrap(sslBuffer);
                        break out;
                    }
                    case NOT_HANDSHAKING:
                    {
                        handshaking = false;
                        // TLS handshake completed, now we are ready
                        super.onReady();
                        break out;
                    }
                    default:
                        throw new IllegalStateException();
                }
            }

            if (!handshaking)
            {
                out: while (sslBuffer.hasRemaining())
                {
                    SSLEngineResult result = sslEngine.unwrap(sslBuffer, buffer);
                    logger.debug("Unwrapping result: {}", result);
                    switch (result.getStatus())
                    {
                        case OK:
                        {
                            buffer.flip();
                            super.onRead(buffer);
                            buffer.clear();
                            break;
                        }
                        case CLOSED:
                        {
                            handshakeWrap(sslBuffer);
                            break out;
                        }
                    }
                }
            }
        }
        catch (SSLException x)
        {
            close();
            throw new RuntimeIOException(x);
        }
        finally
        {
            sslByteBuffers.release(buffer);
        }
    }

    @Override
    public void close(ChannelStreamType type)
    {
        // TODO
        super.close(type);
    }

    @Override
    public void close()
    {
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
                        handshakeWrap(sslBuffer);
                        break;
                    }
                    case NEED_UNWRAP:
                    {
                        super.close();
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
        ByteBuffer sslBuffer = sslByteBuffers.acquire(sslEngine.getSession().getPacketBufferSize(), false);
        try
        {
            SSLEngineResult result = sslEngine.wrap(buffer, sslBuffer);
            logger.debug("Wrapping result: {}", result);
            sslBuffer.flip();
            int length = sslBuffer.remaining();
            flush(sslBuffer);
            return length;
        }
        catch (SSLException x)
        {
            close();
            throw new RuntimeIOException(x);
        }
        finally
        {
            sslByteBuffers.release(sslBuffer);
        }
    }

    private boolean handshakeUnwrap(ByteBuffer sslBuffer, ByteBuffer buffer) throws SSLException
    {
        while (sslBuffer.hasRemaining())
        {
            SSLEngineResult result = sslEngine.unwrap(sslBuffer, buffer);
            logger.debug("Handshake unwrapping result: {}", result);
            assert result.getStatus() == SSLEngineResult.Status.OK;
            if (result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NEED_UNWRAP)
                return false;
        }
        return true;
    }

    private void handshakeWrap(ByteBuffer sslBuffer) throws SSLException
    {
        // During handshake wrapping there is nothing to send
        ByteBuffer buffer = ByteBuffer.allocate(0);
        while (true)
        {
            sslBuffer.clear();
            SSLEngineResult result = sslEngine.wrap(buffer, sslBuffer);
            logger.debug("Handshake wrapping result: {}", result);
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
    public void onClosing()
    {
        flusher.closeEvent();
        super.onClosing();
    }

    private class SSLFlusher extends Flusher
    {
        private SSLFlusher(Coordinator coordinator)
        {
            super(coordinator);
        }

        @Override
        protected int write(ByteBuffer buffer)
        {
            return SSLInterceptor.super.write(buffer);
        }

        @Override
        protected void close()
        {
            SSLInterceptor.this.close();
        }
    }
}
