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

package org.codehaus.larex.io.connector;

import java.net.InetSocketAddress;

import org.codehaus.larex.io.async.Connection;

/**
 * @version $Revision$ $Date$
 */
public abstract class Endpoint<T extends Connection>
{
    private InetSocketAddress bindAddress;
    private int connectTimeout;
    private int readTimeout;
    private int writeTimeout;

    public InetSocketAddress getBindAddress()
    {
        return bindAddress;
    }

    public void setBindAddress(InetSocketAddress bindAddress)
    {
        this.bindAddress = bindAddress;
    }

    public int getConnectTimeout()
    {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout)
    {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout()
    {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout)
    {
        this.readTimeout = readTimeout;
    }

    public int getWriteTimeout()
    {
        return writeTimeout;
    }

    public void setWriteTimeout(int writeTimeout)
    {
        this.writeTimeout = writeTimeout;
    }

    public abstract T connect(InetSocketAddress address);
}
