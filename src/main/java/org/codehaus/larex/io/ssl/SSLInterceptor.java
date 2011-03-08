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

import org.codehaus.larex.io.BlockingWriter;
import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.Interceptor;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.RuntimeSocketClosedException;
import org.codehaus.larex.io.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSLInterceptor extends Interceptor.Forwarder
{
    private static final Logger logger = LoggerFactory.getLogger(SSLInterceptor.class);
    private final ByteBuffers sslByteBuffers;
    private final SSLEngine sslEngine;
    private final Controller controller;
    private final SSLWriter flusher;
    private volatile boolean handshaking = true;
    private volatile ByteBuffer sslBuffer;

    public SSLInterceptor(ByteBuffers sslByteBuffers, SSLEngine sslEngine, Controller controller)
    {
        this.sslByteBuffers = sslByteBuffers;
        this.sslEngine = sslEngine;
        this.controller = controller;
        this.flusher = new SSLWriter(controller);
    }

    @Override
    public void onOpen()
    {
        try
        {
            // Signal to the SSL engine that we're starting the handshake
            sslEngine.beginHandshake();
            SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
            while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED)
            {
                logger.debug("Open handshake status: {}", handshakeStatus);
                switch (handshakeStatus)
                {
                    case NEED_WRAP:
                    {
                        encryptSSL();
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
                logger.debug("Read handshake status: {}", handshakeStatus);
                switch (handshakeStatus)
                {
                    case NEED_UNWRAP:
                    {
                        if (decryptSSL(sslBuffer, buffer))
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
                        encryptSSL();
                        break out;
                    }
                    default:
                        throw new IllegalStateException();
                }
            }

            // TODO: fix this
/*
            if (handshaking)
                return true;

            return decryptData(sslBuffer, buffer);
*/
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
        return false;
    }

    private boolean decryptData(ByteBuffer sslBuffer, ByteBuffer buffer) throws SSLException
    {
        // TODO: fix this
        boolean readMore = true;

        int bufferSize = sslEngine.getSession().getApplicationBufferSize();
        out: while (sslBuffer.hasRemaining())
        {
            ByteBuffer source = fillLocal(sslBuffer);

            logger.debug("Decrypting from {} to {}", source, buffer);
            SSLEngineResult result = sslEngine.unwrap(source, buffer);
            logger.debug("Decrypted from {} to {}, result {}", new Object[]{source, buffer, result});
            switch (result.getStatus())
            {
                case OK:
                {
                    // Prepare for read
                    buffer.flip();
                    // Forward the call with the unencrypted bytes
                    logger.debug("Forwarding read event of {} bytes", buffer.remaining());
                    super.onRead(buffer);
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
                    // We were expecting data from the remote peer, we got a
                    // close message instead, forward the remote close event
                    logger.debug("Forwarding remote close event");
                    super.onRemoteClose();
                    encryptSSL();
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
            closeSSLInput();
        }
        catch (SSLException x)
        {
            // Could be a truncation attack where a man in the middle
            // truncates the message then sends a FIN.
            // Do not forward the remote close event, since the connection
            // was not orderly closed by the remote peer.
            logger.debug("", x);
        }
    }

    @Override
    public void close(StreamType type)
    {
        logger.debug("Closing {}", type);
        switch (type)
        {
            case INPUT:
            {
                // SSLEngine.closeInbound() can only be called after receiving
                // the remote peer SSL close message, so we just forward the close
                break;
            }
            case OUTPUT:
            case INPUT_OUTPUT:
            {
                try
                {
                    closeSSLOutput();
                    break;
                }
                catch (SSLException x)
                {
                    throw new RuntimeIOException(x);
                }
            }
            default:
                throw new IllegalStateException();
        }
        super.close(type);
    }

    protected void closeSSLInput() throws SSLException
    {
        sslEngine.closeInbound();
        SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        logger.debug("Close inbound handshake status: {}", handshakeStatus);
        assert handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING : handshakeStatus;
    }

    protected void closeSSLOutput() throws SSLException
    {
        ByteBuffer sslBuffer = sslByteBuffers.acquire(sslEngine.getSession().getPacketBufferSize(), false);
        try
        {
            sslEngine.closeOutbound();
            SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
            out: while (handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING)
            {
                logger.debug("Close outbound handshake status: {}", handshakeStatus);
                switch (handshakeStatus)
                {
                    case NEED_WRAP:
                    {
                        encryptSSL();
                        break;
                    }
                    case NEED_UNWRAP:
                    {
                        // The TLS specification allows the initiator of the close to
                        // close the connection without reading the peer response.
                        // This is good because otherwise we would need to put this
                        // thread in wait, read the response, then call super.close()
                        // which would be more complicated.
                        break out;
                    }
                    default:
                        throw new IllegalStateException();
                }
                handshakeStatus = sslEngine.getHandshakeStatus();
            }
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
            return encryptData(buffer, sslBuffer);
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

    private int encryptData(ByteBuffer buffer, ByteBuffer sslBuffer) throws SSLException
    {
        logger.debug("Encrypting from {} to {}", buffer, sslBuffer);
        SSLEngineResult result = sslEngine.wrap(buffer, sslBuffer);
        logger.debug("Encrypted from {} to {}, result {}", new Object[]{buffer, sslBuffer, result});

        SSLEngineResult.Status status = result.getStatus();
        assert status == SSLEngineResult.Status.OK || status == SSLEngineResult.Status.CLOSED : status;

        // Either we have encrypted data in the buffer, or we have generated the close message
        int written = 0;
        if (result.bytesProduced() > 0)
        {
            sslBuffer.flip();
            written = sslBuffer.remaining();
            flush(sslBuffer);
        }

        if (status == SSLEngineResult.Status.CLOSED && result.bytesConsumed() == 0 && buffer.hasRemaining())
        {
            // Trying to write after the SSL engine has received the close message
            // This is currently not supported by the SSL engine
            throw new RuntimeSocketClosedException("Writing data on a closed SSLEngine is not supported");
        }

        return written;
    }

    private boolean decryptSSL(ByteBuffer sslBuffer, ByteBuffer buffer) throws SSLException
    {
        logger.debug("Handshake reading from {}", sslBuffer);

        // If we have a previous unfinished read, coalesce it
        ByteBuffer source = fillLocal(sslBuffer);

        out: while (source.hasRemaining())
        {
            logger.debug("Handshake decrypting from {} to {}", source, buffer);
            SSLEngineResult result = sslEngine.unwrap(source, buffer);
            logger.debug("Handshake decrypted from {} to {}, result {}", new Object[]{source, buffer, result});
            switch (result.getStatus())
            {
                case OK:
                {
                    // Everything is decrypted, we're done
                    resetLocal();
                    break;
                }
                case BUFFER_UNDERFLOW:
                {
                    prepareLocal(sslBuffer);
                    break out;
                }
                case CLOSED:
                {
                    // We have decrypted an SSL close message, just break
                    break;
                }
                default:
                    throw new IllegalStateException();
            }
            SSLEngineResult.HandshakeStatus handshakeStatus = result.getHandshakeStatus();
            if (handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED)
            {
                handshaking = false;
                logger.debug("Handshake finished (client), forwarding open event");
                super.onOpen();
            }
            if (handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_UNWRAP)
                return false;
        }
        return true;
    }

    private void encryptSSL() throws SSLException
    {
        int sslBufferSize = sslEngine.getSession().getPacketBufferSize();
        ByteBuffer sslBuffer = sslByteBuffers.acquire(sslBufferSize, false);
        try
        {
            // During handshake encryption there is nothing to send
            ByteBuffer buffer = ByteBuffer.allocate(0);
            while (true)
            {
                sslBuffer.clear();
                sslBuffer.limit(sslBufferSize);
                logger.debug("Handshake encrypting to {}", sslBuffer);
                SSLEngineResult result = sslEngine.wrap(buffer, sslBuffer);
                logger.debug("Handshake encrypted to {}, result {}", sslBuffer, result);
                SSLEngineResult.Status status = result.getStatus();
                assert status == SSLEngineResult.Status.OK || status == SSLEngineResult.Status.CLOSED : status;
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
                SSLEngineResult.HandshakeStatus handshakeStatus = result.getHandshakeStatus();
                if (handshakeStatus == SSLEngineResult.HandshakeStatus.FINISHED)
                {
                    handshaking = false;
                    logger.debug("Handshake finished (server), forwarding open event");
                    super.onOpen();
                }
                if (handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_WRAP)
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
        flusher.write(sslBuffer);
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

    private class SSLWriter extends BlockingWriter
    {
        private SSLWriter(Controller controller)
        {
            super(controller);
        }

        @Override
        protected int write(Controller controller, ByteBuffer buffer)
        {
            return SSLInterceptor.super.write(buffer);
        }
    }
}
