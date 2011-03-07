package org.codehaus.larex.io;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link NonBlockingWriter}
 */
public class NonBlockingWriterTest
{
    @Test
    public void testFullWrite() throws Exception
    {
        class C extends EmptyController
        {
            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                return write(buffer, buffer.remaining());
            }
        }

        NonBlockingWriter writer = new NonBlockingWriter(new C());
        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int written = writer.write(buffer);

        assertEquals(bytes.length, written);
        assertFalse(buffer.hasRemaining());
        assertFalse(writer.getBuffer().hasRemaining());
    }

    @Test
    public void testPartialWrite() throws Exception
    {
        final AtomicInteger needsWrites = new AtomicInteger();
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public void needsWrite(boolean needsWrite)
            {
                if (needsWrite)
                    needsWrites.incrementAndGet();
            }

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                {
                    return write(buffer, buffer.remaining() - 1);
                }
                else
                {
                    return write(buffer, buffer.remaining());
                }
            }
        }

        NonBlockingWriter writer = new NonBlockingWriter(new C());
        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int written = writer.write(buffer);

        assertEquals(1, needsWrites.get());
        assertFalse(buffer.hasRemaining());
        assertEquals(bytes.length, written + writer.getBuffer().remaining());

        writer.writeReadyEvent();

        assertEquals(1, needsWrites.get());
        assertFalse(writer.getBuffer().hasRemaining());
    }

    @Test
    public void testDoublePartialWrite() throws Exception
    {
        final AtomicInteger needsWrites = new AtomicInteger();
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public void needsWrite(boolean needsWrite)
            {
                if (needsWrite)
                    needsWrites.incrementAndGet();
            }

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                {
                    return write(buffer, buffer.remaining() - 2);
                }
                else if (w == 2)
                {
                    return write(buffer, buffer.remaining() - 1);
                }
                else
                {
                    return write(buffer, buffer.remaining());
                }
            }
        }

        NonBlockingWriter writer = new NonBlockingWriter(new C());
        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int written = writer.write(buffer);

        assertEquals(1, needsWrites.get());
        assertFalse(buffer.hasRemaining());
        assertEquals(bytes.length, written + writer.getBuffer().remaining());

        writer.writeReadyEvent();

        assertEquals(2, needsWrites.get());
        assertTrue(writer.getBuffer().hasRemaining());

        writer.writeReadyEvent();

        assertEquals(2, needsWrites.get());
        assertFalse(writer.getBuffer().hasRemaining());
    }

    @Test
    public void testPartialWriteWithWriteAppended() throws Exception
    {
        final AtomicInteger needsWrites = new AtomicInteger();
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public void needsWrite(boolean needsWrite)
            {
                if (needsWrite)
                    needsWrites.incrementAndGet();
            }

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                {
                    return write(buffer, buffer.remaining() - 2);
                }
                else
                {
                    return write(buffer, buffer.remaining());
                }
            }
        }

        NonBlockingWriter writer = new NonBlockingWriter(new C());

        byte[] bytes1 = "hello".getBytes("UTF-8");
        ByteBuffer buffer1 = ByteBuffer.wrap(bytes1);
        int written1 = writer.write(buffer1);

        assertEquals(1, needsWrites.get());
        assertFalse(buffer1.hasRemaining());
        assertEquals(bytes1.length, written1 + writer.getBuffer().remaining());

        byte[] bytes2 = "world".getBytes("UTF-8");
        ByteBuffer buffer2 = ByteBuffer.wrap(bytes2);
        int written2 = writer.write(buffer2);

        assertEquals(1, needsWrites.get());
        assertEquals(bytes2.length, written2);
        assertFalse(buffer2.hasRemaining());
        assertFalse(writer.getBuffer().hasRemaining());
    }

    @Test
    public void testDoublePartialWriteWithWriteAppended() throws Exception
    {
        final AtomicInteger needsWrites = new AtomicInteger();
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public void needsWrite(boolean needsWrite)
            {
                if (needsWrite)
                    needsWrites.incrementAndGet();
            }

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                {
                    return write(buffer, buffer.remaining() - 2);
                }
                else if (w == 2)
                {
                    return write(buffer, buffer.remaining() - 1);
                }
                else
                {
                    return write(buffer, buffer.remaining());
                }
            }
        }

        NonBlockingWriter writer = new NonBlockingWriter(new C());

        byte[] bytes1 = "hello".getBytes("UTF-8");
        ByteBuffer buffer1 = ByteBuffer.wrap(bytes1);
        int written1 = writer.write(buffer1);

        assertEquals(1, needsWrites.get());
        assertFalse(buffer1.hasRemaining());
        assertEquals(bytes1.length, written1 + writer.getBuffer().remaining());

        byte[] bytes2 = "world".getBytes("UTF-8");
        ByteBuffer buffer2 = ByteBuffer.wrap(bytes2);
        int written2 = writer.write(buffer2);

        assertEquals(2, needsWrites.get());
        assertEquals(0, written2);
        assertFalse(buffer2.hasRemaining());
        assertTrue(writer.getBuffer().remaining() > bytes2.length);

        writer.writeReadyEvent();

        assertEquals(2, needsWrites.get());
        assertFalse(writer.getBuffer().hasRemaining());
    }

    @Test
    public void testConcurrentWrites() throws Exception
    {
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                {
                    return write(buffer, buffer.remaining() - 2);
                }
                else
                {
                    return write(buffer, buffer.remaining());
                }
            }
        }

        final long sleep = 500;
        final CountDownLatch latch = new CountDownLatch(1);
        final NonBlockingWriter writer = new NonBlockingWriter(new C())
        {
            @Override
            protected boolean writeBuffer()
            {
                // Simulate that writing the buffer takes a while
                // so we can test that it's correctly synchronized

                try
                {
                    latch.countDown();
                    TimeUnit.MILLISECONDS.sleep(500);
                    return super.writeBuffer();
                }
                catch (InterruptedException x)
                {
                    Thread.currentThread().interrupt();
                    throw new RuntimeSocketTimeoutException(x);
                }
            }
        };

        byte[] bytes1 = "hello".getBytes("UTF-8");
        ByteBuffer buffer1 = ByteBuffer.wrap(bytes1);
        int written1 = writer.write(buffer1);

        assertTrue(written1 < bytes1.length);

        byte[] bytes2 = "world".getBytes("UTF-8");
        ByteBuffer buffer2 = ByteBuffer.wrap(bytes2);

        new Thread()
        {
            @Override
            public void run()
            {
                writer.writeReadyEvent();
            }
        }.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        long start = System.nanoTime();
        int written2 = writer.write(buffer2);
        long end = System.nanoTime();

        assertTrue(TimeUnit.NANOSECONDS.toMillis(end - start) > sleep / 2);
        assertEquals(bytes2.length, written2);
    }

    @Test
    public void testPartialWriteWithClose()throws Exception
    {
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                {
                    return write(buffer, buffer.remaining() - 2);
                }
                else
                {
                    return write(buffer, buffer.remaining());
                }
            }
        }

        C controller = new C();
        NonBlockingWriter writer = new NonBlockingWriter(controller);

        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int written = writer.write(buffer);

        assertTrue(bytes.length > written);

        writer.closingEvent();

        assertEquals(2, controller.writes.get());
        assertFalse(writer.getBuffer().hasRemaining());
    }

    @Test
    public void testPartialWriteWithTimeout()throws Exception
    {
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                {
                    return write(buffer, buffer.remaining() - 2);
                }
                else
                {
                    return write(buffer, buffer.remaining());
                }
            }
        }

        C controller = new C();
        NonBlockingWriter writer = new NonBlockingWriter(controller);

        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int written = writer.write(buffer);

        assertTrue(bytes.length > written);

        writer.writeTimeoutEvent();

        assertEquals(1, controller.writes.get());
        assertFalse(writer.getBuffer().hasRemaining());
    }

    @Test
    public void testWriteThrows() throws Exception
    {
        class C extends EmptyController
        {
            private final AtomicInteger writes = new AtomicInteger();

            @Override
            public int write(ByteBuffer buffer) throws RuntimeSocketClosedException
            {
                int w = writes.incrementAndGet();
                if (w == 1)
                {
                    return write(buffer, buffer.remaining() - 2);
                }
                else
                {
                    throw new RuntimeIOException();
                }
            }
        }

        C controller = new C();
        NonBlockingWriter writer = new NonBlockingWriter(controller);

        byte[] bytes = "hello".getBytes("UTF-8");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int written = writer.write(buffer);

        assertTrue(bytes.length > written);

        try
        {
            writer.writeReadyEvent();
            fail();
        }
        catch (RuntimeIOException x)
        {
            assertTrue(writer.getBuffer().hasRemaining());

            // A close from a client of this class would
            // trigger closingEvent()
            writer.closingEvent();

            assertFalse(writer.getBuffer().hasRemaining());
        }
    }

}
