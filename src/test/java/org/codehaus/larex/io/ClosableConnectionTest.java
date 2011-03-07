package org.codehaus.larex.io;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ClosableConnection}
 */
public class ClosableConnectionTest
{
    @Test
    public void testSoftClose() throws Exception
    {
        class C extends EmptyController
        {
            private Connection connection;

            public void setConnection(Connection connection)
            {
                this.connection = connection;
            }

            @Override
            public void close(StreamType type)
            {
                // In the standard implementation of Controller,
                // when a remote close is detected, the connection
                // is notified, and then the connection is closed
                // Below we simulate exactly that when the output
                // is closed (see StandardCoordinator#onRemoteClose())

                if (type == StreamType.OUTPUT)
                    close(StreamType.INPUT_OUTPUT);
                else if (type == StreamType.INPUT_OUTPUT)
                    connection.closedEvent(type);
            }
        }

        C controller = new C();
        ClosableConnection connection = new ClosableConnection(controller)
        {
        };
        controller.setConnection(connection);

        long timeout = 1000;
        long start = System.nanoTime();
        assertTrue(connection.softClose(timeout));
        long end = System.nanoTime();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(end - start) < timeout / 2);
    }

    @Test
    public void testSoftCloseTimeout() throws Exception
    {
        class C extends EmptyController
        {
            @Override
            public void close(StreamType type)
            {
                // Simulate that the remote peer does not close by doing
                // nothing, so this peer is not notified of the remote close
            }
        }

        ClosableConnection connection = new ClosableConnection(new C())
        {
        };

        long timeout = 500;
        long start = System.nanoTime();
        assertFalse(connection.softClose(timeout));
        long end = System.nanoTime();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(end - start) >= timeout);
    }
}
