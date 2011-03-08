package org.codehaus.larex.io;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BlockingWriterTest
{
    @Test
    public void testWriteBlocksUntilWriteEvent() throws Exception
    {
        final BlockingWriter writer = new BlockingWriter(new EmptyController()
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                    return write(buffer, buffer.remaining() - 1);
                return write(buffer, buffer.remaining());
            }
        });

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                writer.writeReadyEvent();
            }
        }.start();

        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long begin = System.nanoTime();
        writer.write(buffer);
        long end = System.nanoTime();

        assertFalse(buffer.hasRemaining());
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testWriteBlocksUntilWriteTimeoutEvent() throws Exception
    {
        final BlockingWriter writer = new BlockingWriter(new EmptyController()
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                    return write(buffer, buffer.remaining() - 1);
                return write(buffer, buffer.remaining());
            }
        });

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                writer.writeTimeoutEvent();
            }
        }.start();

        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long begin = System.nanoTime();
        try
        {
            writer.write(buffer);
            fail();
        }
        catch (RuntimeSocketTimeoutException x)
        {
        }
        long end = System.nanoTime();

        assertTrue(buffer.hasRemaining());
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testWriteBlocksUntilCloseEvent() throws Exception
    {
        final BlockingWriter writer = new BlockingWriter(new EmptyController()
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                    return write(buffer, buffer.remaining() - 1);
                return write(buffer, buffer.remaining());
            }
        });

        final long sleep = 500;
        new Thread()
        {
            @Override
            public void run()
            {
                sleepFor(sleep);
                writer.closingEvent();
            }
        }.start();

        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long begin = System.nanoTime();
        try
        {
            writer.write(buffer);
            fail();
        }
        catch (RuntimeSocketClosedException x)
        {
        }
        long end = System.nanoTime();

        assertTrue(buffer.hasRemaining());
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testWriteBlocksUntilInterrupted() throws Exception
    {
        final BlockingWriter writer = new BlockingWriter(new EmptyController()
        {
            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                return write(buffer, buffer.remaining() - 1);
            }
        });

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

        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long begin = System.nanoTime();
        try
        {
            writer.write(buffer);
            fail();
        }
        catch (RuntimeSocketClosedException x)
        {
            assertTrue(x.getCause() instanceof ClosedByInterruptException);
        }
        long end = System.nanoTime();

        assertTrue(buffer.hasRemaining());
        assertTrue(TimeUnit.NANOSECONDS.toNanos(end - begin) >= TimeUnit.MILLISECONDS.toNanos(sleep));
    }

    @Test
    public void testConcurrentWriteAndCloseThrows() throws Exception
    {
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();
            private volatile BlockingWriter writer;

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int result = write(buffer, buffer.remaining() - 1);
                writer.closingEvent();
                return result;
            }
        }
        C controller = new C();
        final BlockingWriter writer = new BlockingWriter(controller);
        controller.writer = writer;

        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        try
        {
            writer.write(buffer);
            fail();
        }
        catch (RuntimeSocketClosedException x)
        {
        }

        assertTrue(buffer.hasRemaining());
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
