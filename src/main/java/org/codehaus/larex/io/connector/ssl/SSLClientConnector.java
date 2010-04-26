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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.Executor;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.CachedByteBuffers;
import org.codehaus.larex.io.Connection;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.RuntimeIOException;
import org.codehaus.larex.io.Scheduler;
import org.codehaus.larex.io.Selector;
import org.codehaus.larex.io.connector.StandardClientConnector;

/**
 * @version $Revision$ $Date$
 */
public class SSLClientConnector extends StandardClientConnector
{
    private final ByteBuffers sslByteBuffers;
    private volatile String protocolAlgorithm = "SSLv3";
    private volatile String keyStoreType = "JKS";
    private volatile String keyStoreResource = System.getProperty("user.home") + File.separator + ".keystore";
    private volatile String keyStorePassword = null;
    private volatile String keyPassword = null;
    private volatile String keyStoreAlgorithm = "SunX509";
    private volatile String trustStoreType = "JKS";
    private volatile String trustStoreResource = null;
    private volatile String trustStorePassword = null;
    private volatile String trustStoreAlgorithm = "SunX509";
    private volatile String secureRandomAlgorithm = "SHA1PRNG";
    private volatile SSLContext sslContext;

    public SSLClientConnector(Executor threadPool, Scheduler scheduler)
    {
        this(threadPool, scheduler, 1);
    }

    public SSLClientConnector(Executor threadPool, Scheduler scheduler, int selectors)
    {
        super(threadPool, scheduler, selectors);
        this.sslByteBuffers = newSSLByteBuffers();
    }

    protected ByteBuffers newSSLByteBuffers()
    {
        return new CachedByteBuffers();
    }

    public String getProtocolAlgorithm()
    {
        return protocolAlgorithm;
    }

    public void setProtocolAlgorithm(String protocolAlgorithm)
    {
        this.protocolAlgorithm = protocolAlgorithm;
    }

    public String getKeyStoreType()
    {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType)
    {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyStoreResource()
    {
        return keyStoreResource;
    }

    public void setKeyStoreResource(String keyStoreResource)
    {
        this.keyStoreResource = keyStoreResource;
    }

    public String getKeyStorePassword()
    {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword)
    {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyPassword()
    {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword)
    {
        this.keyPassword = keyPassword;
    }

    public String getKeyStoreAlgorithm()
    {
        return keyStoreAlgorithm;
    }

    public void setKeyStoreAlgorithm(String keyStoreAlgorithm)
    {
        this.keyStoreAlgorithm = keyStoreAlgorithm;
    }

    public String getTrustStoreType()
    {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType)
    {
        this.trustStoreType = trustStoreType;
    }

    public String getTrustStoreResource()
    {
        return trustStoreResource;
    }

    public void setTrustStoreResource(String trustStoreResource)
    {
        this.trustStoreResource = trustStoreResource;
    }

    public String getTrustStorePassword()
    {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword)
    {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStoreAlgorithm()
    {
        return trustStoreAlgorithm;
    }

    public void setTrustStoreAlgorithm(String trustStoreAlgorithm)
    {
        this.trustStoreAlgorithm = trustStoreAlgorithm;
    }

    public String getSecureRandomAlgorithm()
    {
        return secureRandomAlgorithm;
    }

    public void setSecureRandomAlgorithm(String secureRandomAlgorithm)
    {
        this.secureRandomAlgorithm = secureRandomAlgorithm;
    }

    @Override
    public <C extends Connection> SSLEndpoint<C> newEndpoint(ConnectionFactory<C> connectionFactory)
    {
        return (SSLEndpoint<C>)super.newEndpoint(connectionFactory);
    }

    @Override
    protected <C extends Connection> SSLEndpoint<C> newEndpoint(Selector selector, ConnectionFactory<C> connectionFactory, ByteBuffers byteBuffers, Executor threadPool, Scheduler scheduler)
    {
        try
        {
            return new SSLEndpoint<C>(selector, connectionFactory, byteBuffers, threadPool, scheduler, getSSLContext(), sslByteBuffers);
        }
        catch (Exception x)
        {
            throw new RuntimeIOException(x);
        }
    }

    public SSLContext getSSLContext() throws Exception
    {
        if (sslContext == null)
        {
            KeyStore keyStore = getKeyStore(getKeyStoreType(), getKeyStoreResource(), getKeyStorePassword());
            KeyManager[] keyManagers = getKeyManagers(keyStore);

            KeyStore trustStore = getKeyStore(getTrustStoreType(), getTrustStoreResource(), getTrustStorePassword());
            TrustManager[] trustManagers = getTrustManagers(trustStore);

            SecureRandom secureRandom = SecureRandom.getInstance(getSecureRandomAlgorithm());
            SSLContext context = SSLContext.getInstance(getProtocolAlgorithm());
            context.init(keyManagers, trustManagers, secureRandom);
            sslContext = context;
        }
        return sslContext;
    }

    protected KeyStore getKeyStore(String keyStoreType, String keyStoreResource, String keyStorePassword) throws Exception
    {
        if (keyStoreResource == null)
            return null;
        InputStream keyStoreStream = getClass().getClassLoader().getResourceAsStream(keyStoreResource);
        if (keyStoreStream == null)
        {
            File keyStoreFile = new File(keyStoreResource);
            if (keyStoreFile.exists() && keyStoreFile.canRead())
                keyStoreStream = new FileInputStream(keyStoreFile);
        }
        if (keyStoreStream == null)
            return null;
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(keyStoreStream, keyStorePassword == null ? null : keyStorePassword.toCharArray());
        keyStoreStream.close();
        return keyStore;
    }

    protected KeyManager[] getKeyManagers(KeyStore keyStore) throws Exception
    {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(getKeyStoreAlgorithm());
        String password = getKeyPassword() == null ? getKeyStorePassword() : getKeyPassword();
        keyManagerFactory.init(keyStore, password == null ? null : password.toCharArray());
        return keyManagerFactory.getKeyManagers();
    }

    protected TrustManager[] getTrustManagers(KeyStore trustStore) throws Exception
    {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(getTrustStoreAlgorithm());
        trustManagerFactory.init(trustStore);
        return trustManagerFactory.getTrustManagers();
    }
}
