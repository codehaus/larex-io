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
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * @version $Revision$ $Date$
 */
public class CachedByteBuffers implements ByteBuffers
{
    private final ConcurrentMap<Integer, Queue<ByteBuffer>> directBuffers = new ConcurrentHashMap<Integer, Queue<ByteBuffer>>();
    private final ConcurrentMap<Integer, Queue<ByteBuffer>> heapBuffers = new ConcurrentHashMap<Integer, Queue<ByteBuffer>>();
    private final int factor = 1024;

    public ByteBuffer acquire(int size, boolean direct)
    {
        int bucket = size / factor;
        ConcurrentMap<Integer, Queue<ByteBuffer>> buffers = direct ? directBuffers : heapBuffers;

        // Avoid to create a new queue every time, just to be discarded immediately
        Queue<ByteBuffer> byteBuffers = buffers.get(bucket);
        if (byteBuffers == null)
        {
            byteBuffers = new ConcurrentLinkedQueue<ByteBuffer>();
            Queue<ByteBuffer> existing = buffers.putIfAbsent(bucket, byteBuffers);
            if (existing != null)
                byteBuffers = existing;
        }

        ByteBuffer result = byteBuffers.poll();
        while (result == null)
        {
            int capacity = (bucket + 1) * factor;
            result = direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
            byteBuffers.offer(result);
            result = byteBuffers.poll();
        }

        result.clear();
        result.limit(size);

        return result;
    }

    public void release(ByteBuffer buffer)
    {
        int bucket = buffer.capacity() / factor;
        ConcurrentMap<Integer, Queue<ByteBuffer>> buffers = buffer.isDirect() ? directBuffers : heapBuffers;
        Queue<ByteBuffer> byteBuffers = buffers.get(bucket);
        if (byteBuffers != null)
            byteBuffers.offer(buffer);
    }
}
