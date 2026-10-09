package com.minicord.clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;

/**
 * Vector Clock implementation for causal ordering.
 * 
 * DC Topic: Clock Synchronization
 * 
 * Each server maintains a vector of N counters (one per server).
 * Vector clock[i] = number of events known from server i.
 * 
 * Rules:
 * - On local event: increment own index
 * - On send: increment own index, attach vector
 * - On receive: merge (element-wise max), then increment own index
 * 
 * Comparison:
 * - V1 < V2  iff all V1[i] <= V2[i] AND at least one V1[i] < V2[i] (happened-before)
 * - V1 || V2 iff neither V1 < V2 nor V2 < V1 (concurrent)
 */
@Component
public class VectorClock {

    @Value("${minicord.server.id}")
    private int serverId;

    @Value("${minicord.server.total-nodes}")
    private int totalNodes;

    private long[] clock;

    @PostConstruct
    public void init() {
        clock = new long[totalNodes];
        Arrays.fill(clock, 0);
    }

    /**
     * Increment own index for a local event.
     * @return copy of the updated vector
     */
    public synchronized long[] tick() {
        clock[serverId - 1]++;
        return getClock();
    }

    /**
     * Send event: increment own index and return vector to attach to message.
     * @return copy of the vector clock
     */
    public synchronized long[] send() {
        clock[serverId - 1]++;
        return getClock();
    }

    /**
     * Receive event: merge with received vector (element-wise max), then increment own index.
     * @param received the vector clock from the incoming message
     * @return copy of the updated vector
     */
    public synchronized long[] receive(long[] received) {
        if (received != null) {
            int len = Math.min(clock.length, received.length);
            for (int i = 0; i < len; i++) {
                clock[i] = Math.max(clock[i], received[i]);
            }
        }
        clock[serverId - 1]++;
        return getClock();
    }

    public synchronized long[] merge(long[] received) {
        return receive(received);
    }

    /**
     * Get a copy of the current vector clock.
     */
    public synchronized long[] getClock() {
        return Arrays.copyOf(clock, clock.length);
    }

    /**
     * Check if vector a happened-before vector b.
     * a < b iff all a[i] <= b[i] AND at least one a[i] < b[i]
     */
    public static boolean happenedBefore(long[] a, long[] b) {
        if (a == null || b == null) return false;
        int len = Math.min(a.length, b.length);
        boolean atLeastOneStrictlyLess = false;
        for (int i = 0; i < len; i++) {
            if (a[i] > b[i]) return false;
            if (a[i] < b[i]) atLeastOneStrictlyLess = true;
        }
        return atLeastOneStrictlyLess;
    }

    /**
     * Check if two events are concurrent (neither happened-before the other).
     */
    public static boolean isConcurrent(long[] a, long[] b) {
        return !happenedBefore(a, b) && !happenedBefore(b, a) && !Arrays.equals(a, b);
    }

    /**
     * Merge two vector clocks (element-wise max).
     */
    public static long[] merge(long[] a, long[] b) {
        int len = Math.max(a.length, b.length);
        long[] result = new long[len];
        for (int i = 0; i < len; i++) {
            long va = i < a.length ? a[i] : 0;
            long vb = i < b.length ? b[i] : 0;
            result[i] = Math.max(va, vb);
        }
        return result;
    }

    @Override
    public synchronized String toString() {
        return "VectorClock" + Arrays.toString(clock);
    }
}
