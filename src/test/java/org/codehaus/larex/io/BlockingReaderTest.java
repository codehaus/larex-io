/*
 * Copyright (c) 2011 the original author or authors.
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
import java.nio.channels.ClosedByInterruptException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link BlockingReader}
 */
public class BlockingReaderTest
{
    @Test
    public void testReadBlocksUntilReadEvent() throws Exception
    {
        final BlockingReader reader = new BlockingReader(new EmptyController());

        byte[] bytes = new byte[256];
        Arrays.fill(bytes, (byte)'x');
        final ByteBuffer data = ByteBuffer.wrap(bytes);

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                reader.readEvent(data);
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        long begin = System.nanoTime();
        int read = reader.read(buffer);
        long end = System.nanoTime();

        assertEquals(buffer.capacity(), read);
        assertEquals(buffer.capacity(), buffer.position());
        assertEquals(0, reader.available());
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testReadBlocksUntilReadEventDifferentBufferCapacities() throws Exception
    {
        final BlockingReader reader = new BlockingReader(new EmptyController());

        byte[] bytes = new byte[256];
        Arrays.fill(bytes, (byte)'x');
        final ByteBuffer data = ByteBuffer.wrap(bytes);

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                reader.readEvent(data);
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(bytes.length - 1);
        long begin = System.nanoTime();
        int read = reader.read(buffer);
        long end = System.nanoTime();

        assertEquals(buffer.capacity(), read);
        assertEquals(buffer.capacity(), buffer.position());
        int available = bytes.length - buffer.capacity();
        assertEquals(available, reader.available());
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));

        buffer.clear();
        read = reader.read(buffer);
        assertEquals(available, read);
        assertEquals(available, buffer.position());
        assertEquals(0, reader.available());
    }

    @Test
    public void testReadBlocksUntilReadTimeoutEvent() throws Exception
    {
        final BlockingReader reader = new BlockingReader(new EmptyController());

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                reader.readTimeoutEvent();
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(256);
        long begin = System.nanoTime();
        try
        {
            reader.read(buffer);
            fail();
        }
        catch (RuntimeSocketTimeoutException e)
        {
        }
        long end = System.nanoTime();

        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testReadBlocksUntilRemoteCloseEvent() throws Exception
    {
        final BlockingReader reader = new BlockingReader(new EmptyController());

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                reader.remoteCloseEvent();
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(256);
        long begin = System.nanoTime();
        int read = reader.read(buffer);
        long end = System.nanoTime();

        assertEquals(-1, read);
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testReadBlocksUntilLocalCloseEvent() throws Exception
    {
        final BlockingReader reader = new BlockingReader(new EmptyController());

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                reader.closingEvent();
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(256);
        long begin = System.nanoTime();
        try
        {
            reader.read(buffer);
            fail();
        }
        catch (RuntimeSocketClosedException e)
        {
        }
        long end = System.nanoTime();

        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testReadBlocksUntilInterrupted() throws Exception
    {
        final BlockingReader reader = new BlockingReader(new EmptyController());

        final Thread currentThread = Thread.currentThread();

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                currentThread.interrupt();
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(256);
        long begin = System.nanoTime();
        try
        {
            reader.read(buffer);
            fail();
        }
        catch (RuntimeSocketClosedException x)
        {
            assertTrue(x.getCause() instanceof ClosedByInterruptException);
        }
        long end = System.nanoTime();

        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    private void sleepFor(long time)
    {
        try
        {
            TimeUnit.MILLISECONDS.sleep(time);
        }
        catch (InterruptedException x)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeSocketTimeoutException(x);
        }
    }
}
