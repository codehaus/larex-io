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
import java.util.HashMap;
import java.util.Map;

/**
 * TODO: document that this is a per-instance thread local
 *
 * @version $Revision$ $Date$
 */
public class ThreadLocalByteBuffers implements ByteBuffers
{
    private ThreadLocal<Map<Integer, ByteBuffer>> heapBuffers = new ThreadLocal<Map<Integer, ByteBuffer>>()
    {
        @Override
        protected Map<Integer, ByteBuffer> initialValue()
        {
            return new HashMap<Integer, ByteBuffer>();
        }
    };
    private ThreadLocal<Map<Integer, ByteBuffer>> directBuffers = new ThreadLocal<Map<Integer, ByteBuffer>>()
    {
        @Override
        protected Map<Integer, ByteBuffer> initialValue()
        {
            return new HashMap<Integer, ByteBuffer>();
        }
    };
    private final int factor;

    public ThreadLocalByteBuffers()
    {
        this(1024);
    }

    public ThreadLocalByteBuffers(int factor)
    {
        this.factor = factor;
    }

    public ByteBuffer acquire(int size, boolean direct)
    {
        int bucket = size / factor;
        if (size % factor > 0)
            ++bucket;
        Map<Integer, ByteBuffer> byteBuffers = direct ? directBuffers.get() : heapBuffers.get();
        ByteBuffer result = byteBuffers.get(bucket);
        if (result == null)
        {
            int capacity = bucket * factor;
            result = direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
            byteBuffers.put(bucket, result);
        }
        else
        {
            result.clear();
        }
        result.limit(size);
        return result;
    }

    public void release(ByteBuffer buffer)
    {
    }
}
