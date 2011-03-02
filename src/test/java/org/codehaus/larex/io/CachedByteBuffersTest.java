package org.codehaus.larex.io;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentMap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link CachedByteBuffers}
 */
public class CachedByteBuffersTest
{
    @Test
    public void testAcquireRelease() throws Exception
    {
        CachedByteBuffers byteBuffers = new CachedByteBuffers();
        ConcurrentMap<Integer,Queue<ByteBuffer>> buffers = byteBuffers.buffersFor(true);

        ByteBuffer buffer = byteBuffers.acquire(512, true);

        assertTrue(buffer.isDirect());
        assertEquals(512, buffer.remaining());
        assertTrue(buffers.isEmpty());

        byteBuffers.release(buffer);

        assertEquals(1, buffers.size());
    }

    @Test
    public void testAcquireReleaseAcquire() throws Exception
    {
        CachedByteBuffers byteBuffers = new CachedByteBuffers();
        ConcurrentMap<Integer,Queue<ByteBuffer>> buffers = byteBuffers.buffersFor(false);

        ByteBuffer buffer1 = byteBuffers.acquire(512, false);
        byteBuffers.release(buffer1);
        ByteBuffer buffer2 = byteBuffers.acquire(512, false);

        assertSame(buffer1, buffer2);

        byteBuffers.release(buffer2);

        assertEquals(1, buffers.size());
    }

    @Test
    public void testAcquireReleaseClear() throws Exception
    {
        CachedByteBuffers byteBuffers = new CachedByteBuffers();
        ConcurrentMap<Integer,Queue<ByteBuffer>> buffers = byteBuffers.buffersFor(true);

        ByteBuffer buffer = byteBuffers.acquire(512, true);
        byteBuffers.release(buffer);

        assertEquals(1, buffers.size());

        byteBuffers.clear();

        assertTrue(buffers.isEmpty());
    }
}
