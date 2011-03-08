/*
 * Copyright (c) 2010 the original author or authors
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

import java.nio.channels.ClosedChannelException;

/**
 * Wraps {@link ClosedChannelException}s thrown by the I/O Java libraries.
 *
 * @version $Revision: 13 $ $Date$
 */
public class RuntimeSocketClosedException extends RuntimeIOException
{
    public RuntimeSocketClosedException()
    {
    }

    public RuntimeSocketClosedException(String message)
    {
        super(message);
    }

    public RuntimeSocketClosedException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public RuntimeSocketClosedException(Throwable cause)
    {
        super(cause);
    }
}
