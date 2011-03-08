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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class FlowControlTest
{
    @Test
    public void testBlockingWrite() throws Exception
    {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().bind(new InetSocketAddress("localhost", 0));
        int port = server.socket().getLocalPort();

        final SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", port));
        SocketChannel socket = server.accept();
        System.err.printf("send buffer %d, receive buffer %d%n", client.socket().getSendBufferSize(), socket.socket().getReceiveBufferSize());

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        class Closer implements Runnable
        {
            @Override
            public void run()
            {
                try
                {
                    client.close();
                }
                catch (IOException x)
                {
                    throw new RuntimeIOException(x);
                }
            }
        }

        // Write but do not read... how much can we write ?
        String data = "0123456789ABCDEF";
        // i==9 => 8 KiB, i==10 => 16 KiB, i==11 => 32 KiB, etc.
        for (int i = 0; i < 12; ++i)
            data += data;

        int total = 0;
        while (true)
        {
            try
            {
                ScheduledFuture<?> task = scheduler.schedule(new Closer(), 1, TimeUnit.SECONDS);
                // If it can't write it will block in write()
                total += client.write(ByteBuffer.wrap(data.getBytes("UTF-8")));
                task.cancel(false);
            }
            catch (ClosedChannelException x)
            {
                System.err.println("total written = " + total);
                break;
            }
        }
    }

    @Test
    public void testNonBlockingWrite() throws Exception
    {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().bind(new InetSocketAddress("localhost", 0));
        int port = server.socket().getLocalPort();

        SocketChannel client = SocketChannel.open(new InetSocketAddress("localhost", port));
        client.configureBlocking(false);
        SocketChannel socket = server.accept();
        System.err.printf("send buffer %d, receive buffer %d%n", client.socket().getSendBufferSize(), socket.socket().getReceiveBufferSize());

        // Write but do not read... how much can we write ?
        String data = "0123456789ABCDEF";
        // i==9 => 8 KiB, i==10 => 16 KiB, i==11 => 32 KiB, etc.
        for (int i = 0; i < 12; ++i)
            data += data;

        int total = 0;
        int zero = 0;
        while (true)
        {
            int written = client.write(ByteBuffer.wrap(data.getBytes("UTF-8")));
            total += written;
            if (written == 0)
            {
                ++zero;
                if (zero == 5)
                    break;
            }
        }
        System.err.println("total written = " + total);
    }
}
