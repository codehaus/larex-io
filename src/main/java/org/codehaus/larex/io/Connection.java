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
 * <p>{@link Connection} interprets bytes read from the I/O system.</p>
 * <p>The interpretation of the bytes normally happens with push parsers, and may involve calling
 * user code to allow processing of the incoming data and production of outgoing data.</p>
 * <p>User code may be offered a blocking I/O API (such in case of servlets); in such case,
 * the connection must handle the concurrency properly.</p>
 *
 * @version $Revision: 903 $ $Date$
 */
public interface Connection
{
    void onOpen();

    void onRead(ByteBuffer buffer);

    void onReadTimeout();

    void onWrite();

    void onWriteTimeout();

    void onRemoteClose();

    void onClosing();

    void onClosed();
}
