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

public class EmptyController implements Controller
{
    @Override
    public void setReadBufferSize(int size)
    {
    }

    @Override
    public void addInterceptor(Interceptor interceptor)
    {
    }

    @Override
    public boolean removeInterceptor(Interceptor interceptor)
    {
        return false;
    }

    @Override
    public void needsRead(boolean needsRead)
    {
    }

    @Override
    public void needsWrite(boolean needsWrite)
    {
    }

    @Override
    public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
    {
        return 0;
    }

    @Override
    public void close(StreamType type)
    {
    }

    @Override
    public boolean isClosed(StreamType type)
    {
        return false;
    }

    protected int write(ByteBuffer buffer, int count)
    {
        buffer.position(buffer.position() + count);
        return count;
    }
}
