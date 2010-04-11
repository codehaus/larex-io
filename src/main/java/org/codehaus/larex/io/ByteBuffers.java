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

/**
 * <p>A provider of {@link ByteBuffer}s.</p>
 *
 * @version $Revision$ $Date$
 */
public interface ByteBuffers
{
    /**
     * <p>Requests a {@link ByteBuffer} of the given size.</p>
     * <p>The returned buffer may have a bigger capacity but will be limited
     * to the given size.</p>
     *
     * @param size   the size of the buffer
     * @param direct whether the buffer must be direct or not
     * @return the requested buffer
     * @see #release(ByteBuffer)
     */
    ByteBuffer acquire(int size, boolean direct);

    /**
     * Returns the {@link ByteBuffer} requested with {@link #acquire(int, boolean)}.
     *
     * @param buffer the buffer to return
     * @see #acquire(int, boolean)
     */
    void release(ByteBuffer buffer);
}
