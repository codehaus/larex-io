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
 * Unit tests for {@link BlockingConnection}
 */
public class BlockingConnectionTest
{
    @Test
    public void testReadBlocksUntilReadEvent() throws Exception
    {
        final BlockingConnection connection = new BlockingConnection(new EmptyController());
        connection.openEvent();

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
                connection.readEvent(data);
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        long begin = System.nanoTime();
        int read = connection.read(buffer);
        long end = System.nanoTime();

        assertEquals(buffer.capacity(), read);
        assertEquals(buffer.capacity(), buffer.position());
        assertEquals(0, connection.available());
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testReadBlocksUntilReadEventDifferentBufferCapacities() throws Exception
    {
        final BlockingConnection connection = new BlockingConnection(new EmptyController());
        connection.openEvent();

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
                connection.readEvent(data);
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(bytes.length - 1);
        long begin = System.nanoTime();
        int read = connection.read(buffer);
        long end = System.nanoTime();

        assertEquals(buffer.capacity(), read);
        assertEquals(buffer.capacity(), buffer.position());
        int available = bytes.length - buffer.capacity();
        assertEquals(available, connection.available());
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));

        buffer.clear();
        read = connection.read(buffer);
        assertEquals(available, read);
        assertEquals(available, buffer.position());
        assertEquals(0, connection.available());
    }

    @Test
    public void testReadBlocksUntilReadTimeoutEvent() throws Exception
    {
        final BlockingConnection connection = new BlockingConnection(new EmptyController());
        connection.openEvent();

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                connection.readTimeoutEvent();
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(256);
        long begin = System.nanoTime();
        try
        {
            connection.read(buffer);
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
        final BlockingConnection connection = new BlockingConnection(new EmptyController());
        connection.openEvent();

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                connection.remoteCloseEvent();
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(256);
        long begin = System.nanoTime();
        int read = connection.read(buffer);
        long end = System.nanoTime();

        assertEquals(-1, read);
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testReadBlocksUntilLocalCloseEvent() throws Exception
    {
        final BlockingConnection connection = new BlockingConnection(new EmptyController());
        connection.openEvent();

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                connection.closingEvent(StreamType.INPUT_OUTPUT);
            }
        }.start();

        ByteBuffer buffer = ByteBuffer.allocate(256);
        long begin = System.nanoTime();
        try
        {
            connection.read(buffer);
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
        final BlockingConnection connection = new BlockingConnection(new EmptyController());
        connection.openEvent();

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
            connection.read(buffer);
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
