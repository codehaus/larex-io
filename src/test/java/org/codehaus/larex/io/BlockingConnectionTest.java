package org.codehaus.larex.io;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link BlockingConnection}
 */
public class BlockingConnectionTest
{


    @Test
    public void testNoDataToReadBlocksUntilReadTimeout() throws Exception
    {
        final BlockingConnection connection = new BlockingConnection(new EmptyController());
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
    public void testReadBlocksUntilDataIsRead() throws Exception
    {
        final BlockingConnection connection = new BlockingConnection(new EmptyController());

        byte[] bytes = new byte[256];
        Arrays.fill(bytes, (byte)'x');
        final ByteBuffer data = ByteBuffer.wrap(bytes);
        connection.readEvent(data);
        assertFalse(data.hasRemaining());
        data.flip();
        connection.readEvent(data);
        assertFalse(data.hasRemaining());
        connection.readEndEvent();

        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
        int read = connection.read(buffer);
        assertEquals(bytes.length, read);
        assertEquals(bytes.length, buffer.position());
        assertEquals(bytes.length, connection.available());
        buffer.clear();
        read = connection.read(buffer);
        assertEquals(bytes.length, read);
        assertEquals(bytes.length, buffer.position());
        assertEquals(0, connection.available());

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                data.flip();
                connection.readEvent(data);
            }
        }.start();
        long begin = System.nanoTime();
        buffer.clear();
        read = connection.read(buffer);
        long end = System.nanoTime();

        assertEquals(bytes.length, read);
        assertEquals(bytes.length, buffer.position());
        assertEquals(0, connection.available());
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
