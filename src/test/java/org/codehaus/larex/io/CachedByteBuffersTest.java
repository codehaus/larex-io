/*
 * Copyright (c) 2011 the original author or authors
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
