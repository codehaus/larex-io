/*
 * Copyright (c) 2010 the original author or authors
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

package org.codehaus.larex.io.benchmark;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.codehaus.larex.io.CallerBlocksPolicy;
import org.codehaus.larex.io.ConnectionFactory;
import org.codehaus.larex.io.Controller;
import org.codehaus.larex.io.StandardConnection;
import org.codehaus.larex.io.connector.ClientConnector;

public class LoadClientMain
{
    public static void main(String[] args) throws Exception
    {
        new LoadClientMain().run();
    }

    private final List<LatencyConnection> connections = new ArrayList<LatencyConnection>();
    private final AtomicLong start = new AtomicLong();
    private final AtomicLong end = new AtomicLong();
    private final AtomicLong responses = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong minLatency = new AtomicLong();
    private final AtomicLong maxLatency = new AtomicLong();
    private final AtomicLong totLatency = new AtomicLong();
    private final ConcurrentMap<Long, AtomicLong> latencies = new ConcurrentHashMap<Long, AtomicLong>();

    public void run() throws Exception
    {
        int maxThreads = 500;
        ExecutorService threadPool = new ThreadPoolExecutor(maxThreads, maxThreads, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new CallerBlocksPolicy());

        ClientConnector connector = new ClientConnector(threadPool);
        connector.open();

        Random random = new Random();

        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

        String defaultHost = "localhost";
        int defaultPort = 8850;

        System.err.print("server [" + defaultHost + "]: ");
        String value = console.readLine().trim();
        if (value.length() == 0)
            value = defaultHost;
        String host = value;

        System.err.print("port [" + defaultPort + "]: ");
        value = console.readLine().trim();
        if (value.length() == 0)
            value = "" + defaultPort;
        int port = Integer.parseInt(value);

        int connections = 100;
        int batchCount = 1000;
        int batchSize = 5;
        long batchPause = 5;
        int requestSize = 50;

        ConnectionFactory<LatencyConnection> connectionFactory = new ConnectionFactory<LatencyConnection>()
        {
            public LatencyConnection newConnection(Controller controller)
            {
                return new LatencyConnection(controller);
            }
        };
        InetSocketAddress address = new InetSocketAddress(host, port);

        while (true)
        {
            System.err.println("-----");

            System.err.print("connections [" + connections + "]: ");
            value = console.readLine();
            if (value == null)
                break;
            value = value.trim();
            if (value.length() == 0)
                value = "" + connections;
            connections = Integer.parseInt(value);

            System.err.println("Waiting for connections to be ready...");

            // Create or remove the necessary connections
            int currentConnections = this.connections.size();
            while (currentConnections < connections)
            {
                LatencyConnection connection = connector.buildConnection(connectionFactory)
                        .bindAddress(new InetSocketAddress("192.168.0.3", 0))
                        .connect(address);
                if (connection.awaitOpened(1000))
                    this.connections.add(connection);
                currentConnections = this.connections.size();
            }

            while (currentConnections > connections)
            {
                LatencyConnection connection = this.connections.remove(currentConnections - 1);
                connection.close();
                currentConnections = this.connections.size();
            }

            if (currentConnections == 0)
            {
                System.err.println("Clients disconnected");
                break;
            }
            else
            {
                System.err.println("Clients ready");
            }

            currentConnections = this.connections.size();
            if (currentConnections > 0)
            {
                System.err.print("batch count [" + batchCount + "]: ");
                value = console.readLine().trim();
                if (value.length() == 0)
                    value = "" + batchCount;
                batchCount = Integer.parseInt(value);

                System.err.print("batch size [" + batchSize + "]: ");
                value = console.readLine().trim();
                if (value.length() == 0)
                    value = "" + batchSize;
                batchSize = Integer.parseInt(value);

                System.err.print("batch pause [" + batchPause + "]: ");
                value = console.readLine().trim();
                if (value.length() == 0)
                    value = "" + batchPause;
                batchPause = Long.parseLong(value);

                // TODO: too small, and we run into Nagle; too big and we run into Nagle + delayed acks
                System.err.print("request size [" + requestSize + "]: ");
                value = console.readLine().trim();
                if (value.length() == 0)
                    value = "" + requestSize;
                requestSize = Integer.parseInt(value);
                String requestBody = "";
                for (int i = 0; i < requestSize; i++)
                    requestBody += "x";
                byte[] content = requestBody.getBytes("UTF-8");

                LatencyConnection statsConnection = this.connections.get(0);
                statsConnection.send((byte)0xFE, new byte[0]);

                reset();
                long start = System.nanoTime();
                long expected = 0;
                for (int i = 0; i < batchCount; ++i)
                {
                    for (int j = 0; j < batchSize; ++j)
                    {
                        int clientIndex = random.nextInt(this.connections.size());
                        LatencyConnection connection = this.connections.get(clientIndex);
                        connection.send((byte)0x01, content);
                        ++expected;
                    }

                    if (batchPause > 0)
                        Thread.sleep(batchPause);
                }
                long end = System.nanoTime();
                long elapsedNanos = end - start;
                if (elapsedNanos > 0)
                {
                    System.err.print("Messages - Elapsed | Rate = ");
                    System.err.print(TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
                    System.err.print(" ms | ");
                    System.err.print(expected * 1000 * 1000 * 1000 / elapsedNanos);
                    System.err.println(" requests/s ");
                }

                waitForResponses(expected);
                statsConnection.send((byte)0xFF, new byte[0]);
                printReport(expected);
            }
        }

        connector.close();
        connector.join(1000);

        threadPool.shutdown();
        threadPool.awaitTermination(1000, TimeUnit.SECONDS);
    }

    private void reset()
    {
        start.set(0L);
        end.set(0L);
        responses.set(0L);
        failures.set(0L);
        minLatency.set(Long.MAX_VALUE);
        maxLatency.set(0L);
        totLatency.set(0L);
        latencies.clear();
    }

    private void updateLatencies(long start, long end)
    {
        long latency = end - start;

        // Update the latencies using a non-blocking algorithm
        long oldMinLatency = minLatency.get();
        while (latency < oldMinLatency)
        {
            if (minLatency.compareAndSet(oldMinLatency, latency)) break;
            oldMinLatency = minLatency.get();
        }
        long oldMaxLatency = maxLatency.get();
        while (latency > oldMaxLatency)
        {
            if (maxLatency.compareAndSet(oldMaxLatency, latency)) break;
            oldMaxLatency = maxLatency.get();
        }
        totLatency.addAndGet(latency);

        latencies.putIfAbsent(latency, new AtomicLong(0L));
        latencies.get(latency).incrementAndGet();
    }

    private boolean waitForResponses(long expected) throws InterruptedException
    {
        long arrived = responses.get() + failures.get();
        long lastArrived = 0;
        int maxRetries = 20;
        int retries = maxRetries;
        while (arrived < expected)
        {
            System.err.println("Waiting for responses to arrive " + arrived + "/" + expected);
            Thread.sleep(500);
            if (lastArrived == arrived)
            {
                --retries;
                if (retries == 0) break;
            }
            else
            {
                lastArrived = arrived;
                retries = maxRetries;
            }
            arrived = responses.get() + failures.get();
        }
        if (arrived < expected)
        {
            System.err.println("Interrupting wait for responses " + arrived + "/" + expected);
            return false;
        }
        else
        {
            System.err.println("All responses arrived " + arrived + "/" + expected);
            return true;
        }
    }

    public void printReport(long expectedCount)
    {
        long responseCount = responses.get() + failures.get();
        System.err.print("Messages - Success/Failures/Expected = ");
        System.err.print(responses.get());
        System.err.print("/");
        System.err.print(failures.get());
        System.err.print("/");
        System.err.println(expectedCount);

        long elapsedNanos = end.get() - start.get();
        if (elapsedNanos > 0)
        {
            System.err.print("Messages - Elapsed | Rate = ");
            System.err.print(TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
            System.err.print(" ms | ");
            System.err.print(responseCount * 1000 * 1000 * 1000 / elapsedNanos);
            System.err.println(" responses/s ");
        }

        if (latencies.size() > 1)
        {
            long maxLatencyBucketFrequency = 0L;
            long[] latencyBucketFrequencies = new long[20];
            long latencyRange = maxLatency.get() - minLatency.get();
            for (Iterator<Map.Entry<Long, AtomicLong>> entries = latencies.entrySet().iterator(); entries.hasNext();)
            {
                Map.Entry<Long, AtomicLong> entry = entries.next();
                long latency = entry.getKey();
                Long bucketIndex = (latency - minLatency.get()) * latencyBucketFrequencies.length / latencyRange;
                int index = bucketIndex.intValue() == latencyBucketFrequencies.length ? latencyBucketFrequencies.length - 1 : bucketIndex.intValue();
                long value = entry.getValue().get();
                latencyBucketFrequencies[index] += value;
                if (latencyBucketFrequencies[index] > maxLatencyBucketFrequency)
                    maxLatencyBucketFrequency = latencyBucketFrequencies[index];
                entries.remove();
            }

            System.err.println("Messages - Latency Distribution Curve (X axis: Frequency, Y axis: Latency):");
            float totalPercentile = 0F;
            for (int i = 0; i < latencyBucketFrequencies.length; i++)
            {
                long latencyBucketFrequency = latencyBucketFrequencies[i];
                float lastPercentile = totalPercentile;
                float percentile = 100F * latencyBucketFrequency / responseCount;
                totalPercentile += percentile;
                int value = Math.round(latencyBucketFrequency * (float)latencyBucketFrequencies.length / maxLatencyBucketFrequency);
                if (value == latencyBucketFrequencies.length) value = value - 1;
                for (int j = 0; j < value; ++j) System.err.print(" ");
                System.err.print("@");
                for (int j = value + 1; j < latencyBucketFrequencies.length; ++j) System.err.print(" ");
                System.err.print("  _  ");
                System.err.print(TimeUnit.NANOSECONDS.toMillis((latencyRange * (i + 1) / latencyBucketFrequencies.length) + minLatency.get()));
                System.err.printf(" ms (%d, %.2f%%)", latencyBucketFrequency, percentile);
                if (lastPercentile < 50F && totalPercentile >= 50F)
                    System.err.print(" ^50%");
                if (lastPercentile < 75F && totalPercentile >= 75F)
                    System.err.print(" ^75%");
                if (lastPercentile < 90F && totalPercentile >= 90F)
                    System.err.print(" ^90%");
                if (lastPercentile < 95F && totalPercentile >= 95F)
                    System.err.print(" ^95%");
                if (lastPercentile < 99F && totalPercentile >= 99F)
                    System.err.print(" ^99%");
                if (lastPercentile < 99.9F && totalPercentile >= 99.9F)
                    System.err.print(" ^99.9%");
                System.err.println();
            }
        }

        System.err.print("Messages - Latency Min/Ave/Max = ");
        System.err.print(TimeUnit.NANOSECONDS.toMillis(minLatency.get()) + "/");
        System.err.print(responseCount == 0 ? "-/" : TimeUnit.NANOSECONDS.toMillis(totLatency.get() / responseCount) + "/");
        System.err.println(TimeUnit.NANOSECONDS.toMillis(maxLatency.get()) + " ms");
    }

    private class LatencyConnection extends StandardConnection
    {
        private volatile CountDownLatch latch;
        private volatile byte type;
        private volatile int typeBytes;
        private volatile long time;
        private volatile int timeBytes;
        private volatile int length;
        private volatile int lengthBytes;
        private volatile int contentLength;

        private LatencyConnection(Controller controller)
        {
            super(controller);
        }

        @Override
        protected void onOpen()
        {
            getController().setReadBufferSize(16384);
        }

        @Override
        protected boolean onRead(ByteBuffer buffer)
        {
            while (buffer.hasRemaining())
            {
                byte currByte = buffer.get();

                if (typeBytes < 1)
                {
                    type = currByte;
                    ++typeBytes;
                    continue;
                }
                else if (timeBytes < 8)
                {
                    time <<= 8;
                    time += currByte & 0xFF;
                    ++timeBytes;
                    continue;
                }
                else if (lengthBytes < 2)
                {
                    length <<= 8;
                    length += currByte & 0xFF;
                    ++lengthBytes;
                    if (lengthBytes < 2)
                        continue;
                }

                int size = Math.min(length - contentLength, buffer.remaining());
                contentLength += size;
                buffer.position(buffer.position() + size);

                if (contentLength == length)
                {
                    byte type = this.type;
                    this.type = 0;
                    this.typeBytes = 0;
                    long time = this.time;
                    this.time = 0;
                    this.timeBytes = 0;
                    this.length = 0;
                    this.lengthBytes = 0;
                    this.contentLength = 0;

                    if (isControlMessage(type))
                    {
                        latch.countDown();
                    }
                    else
                    {
                        responses.incrementAndGet();
                        if (start.get() == 0L)
                            start.set(time);
                        end.set(time);
                        updateLatencies(time, System.nanoTime());
                    }

                }
            }
            return true;
        }

        public void send(byte type, byte[] content) throws InterruptedException
        {
            if (isControlMessage(type))
                latch = new CountDownLatch(1);

            ByteBuffer buffer = ByteBuffer.allocate(1 + 8 + 2 + content.length);
            buffer.clear();

            buffer.put(type);
            buffer.putLong(System.nanoTime());
            buffer.putShort((short)content.length);
            buffer.put(content);

            buffer.flip();

            write(buffer);

            if (isControlMessage(type))
                latch.await();
        }

        private boolean isControlMessage(byte type)
        {
            return (type & 0xFF) == 0xFE || (type & 0xFF) == 0xFF;
        }
    }
}
