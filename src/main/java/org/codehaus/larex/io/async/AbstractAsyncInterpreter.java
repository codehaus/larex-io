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

package org.codehaus.larex.io.async;

import java.nio.ByteBuffer;

/**
 * @version $Revision$ $Date$
 */
public abstract class AbstractAsyncInterpreter implements AsyncInterpreter
{
    private final AsyncCoordinator coordinator;

    public AbstractAsyncInterpreter(AsyncCoordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    public void onOpen()
    {
        coordinator.needsRead(true);
    }

    public void onRead(ByteBuffer buffer)
    {
        read(buffer);
        coordinator.needsRead(true);
    }

    protected void read(ByteBuffer buffer)
    {
    }

    protected ByteBuffer copy(ByteBuffer source)
    {
        ByteBuffer result = ByteBuffer.allocate(source.remaining());
        result.put(source);
        result.flip();
        return result;
    }

    protected void write(ByteBuffer buffer)
    {
        coordinator.write(buffer);
    }

    protected void close()
    {
        coordinator.close();
    }

    public void onClose()
    {
        closed();
        close();
    }

    protected void closed()
    {
    }
}
