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

package org.codehaus.larex.io.connector.ssl;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.codehaus.larex.io.AbstractTestCase;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.EchoConnection;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @version $Revision$ $Date$
 */
public class SSLServerConnectorTest extends AbstractTestCase
{
    private SSLServerConnector connector;

    public int initServerConnector(ConnectionFactory connectionFactory) throws Exception
    {
        InetSocketAddress address = new InetSocketAddress("localhost", 0);
        connector = new SSLServerConnector(address, connectionFactory, getThreadPool(), getScheduler());
        connector.setKeyStoreResource("keystore");
        connector.setKeyStorePassword("storepwd");
        connector.setKeyPassword("keypwd");
        connector.setTrustStoreResource("truststore");
        return connector.listen();
    }

    @After
    public void destroyServerConnector() throws Exception
    {
        connector.close();
        connector.join(1000);
    }

    @Test
    public void testHandshake() throws Exception
    {
        int port = initServerConnector(new EchoConnection.Factory());

        SSLContext sslContext = connector.getSSLContext();
        SSLSocket sslSocket = (SSLSocket)sslContext.getSocketFactory().createSocket("localhost", port);
        try
        {
            final CountDownLatch latch = new CountDownLatch(1);
            sslSocket.addHandshakeCompletedListener(new HandshakeCompletedListener()
            {
                public void handshakeCompleted(HandshakeCompletedEvent handshakeCompletedEvent)
                {
                    latch.countDown();
                }
            });
            sslSocket.startHandshake();

            assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
        }
        finally
        {
            sslSocket.close();
        }
    }

    // TODO: add more tests
}
