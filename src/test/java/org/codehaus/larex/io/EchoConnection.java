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
 * @version $Revision: 903 $ $Date$
 */
public class EchoConnection extends StandardConnection
{
    public EchoConnection(Coordinator coordinator)
    {
        super(coordinator);
    }

    @Override
    protected void onRead(ByteBuffer buffer)
    {
        flush(buffer);
    }

    public static class Factory implements ConnectionFactory<EchoConnection>
    {
        public EchoConnection newConnection(Coordinator coordinator)
        {
            return new EchoConnection(coordinator);
        }
    }
}
