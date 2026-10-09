package com.minicord.clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Berkeley Clock Synchronization Algorithm implementation.
 * 
 * DC Topic: Clock Synchronization
 * 
 * The coordinator:
 * 1. Requests physical time from all servers (via RMI)
 * 2. Computes the average (excluding outliers)
 * 3. Sends offset adjustments to each server (via RMI)
 * 4. Each server adjusts its clock
 * 
 * This class manages the local physical clock offset.
 * The actual coordination logic runs on the coordinator server.
 */
@Component
public class BerkeleySync {

    private static final Logger log = LoggerFactory.getLogger(BerkeleySync.class);

    /**
     * Clock offset in milliseconds.
     * Positive = ahead of real time, Negative = behind real time.
     * This simulates clock drift without actually changing the system clock.
     */
    private final AtomicLong clockOffset = new AtomicLong(0);

    /**
     * Get the "local time" of this server (real time + offset).
     * This is what gets sent to the coordinator during Berkeley sync.
     */
    public long getLocalTime() {
        return System.currentTimeMillis() + clockOffset.get();
    }

    /**
     * Adjust the clock by the given offset (sent by coordinator).
     * @param adjustmentMs milliseconds to add (positive) or subtract (negative)
     */
    public void adjustClock(long adjustmentMs) {
        long oldOffset = clockOffset.get();
        clockOffset.addAndGet(adjustmentMs);
        log.info("Berkeley: Clock adjusted by {}ms (offset: {} → {})",
                adjustmentMs, oldOffset, clockOffset.get());
    }

    /**
     * Force-set clock skew for demo simulation.
     * @param skewMs milliseconds of artificial skew
     */
    public void setClockSkew(long skewMs) {
        long oldOffset = clockOffset.get();
        clockOffset.set(skewMs);
        log.info("Clock skew set: {} → {}ms", oldOffset, skewMs);
    }

    /**
     * Get the current clock offset.
     */
    public long getClockOffset() {
        return clockOffset.get();
    }

    /**
     * Coordinator method: compute adjustments for all servers.
     * 
     * @param serverTimes array of [serverId, localTimeMs] pairs from all servers
     * @return array of [serverId, adjustmentMs] pairs
     */
    public static long[][] computeAdjustments(long[][] serverTimes) {
        if (serverTimes == null || serverTimes.length == 0) {
            return new long[0][];
        }

        // Step 1: Compute average time
        long sum = 0;
        int count = serverTimes.length;
        for (long[] entry : serverTimes) {
            sum += entry[1];
        }
        long average = sum / count;

        // Step 2: Exclude outliers (> 1 standard deviation from mean)
        double variance = 0;
        for (long[] entry : serverTimes) {
            variance += Math.pow(entry[1] - average, 2);
        }
        double stdDev = Math.sqrt(variance / count);

        // Recompute average without outliers
        long filteredSum = 0;
        int filteredCount = 0;
        for (long[] entry : serverTimes) {
            if (Math.abs(entry[1] - average) <= stdDev || stdDev == 0) {
                filteredSum += entry[1];
                filteredCount++;
            }
        }
        long filteredAverage = filteredCount > 0 ? filteredSum / filteredCount : average;

        // Step 3: Compute adjustment for each server
        long[][] adjustments = new long[serverTimes.length][2];
        for (int i = 0; i < serverTimes.length; i++) {
            adjustments[i][0] = serverTimes[i][0]; // serverId
            adjustments[i][1] = filteredAverage - serverTimes[i][1]; // adjustment
        }

        return adjustments;
    }

    public long getTime() {
        return getLocalTime();
    }

    public void adjustTime(long adj) {
        adjustClock(adj);
    }

    public void setClockOffset(long offset) {
        setClockSkew(offset);
    }

    public long computeAverageTime(java.util.Map<Integer, Long> nodeTimes) {
        if (nodeTimes == null || nodeTimes.isEmpty()) return getLocalTime();
        long sum = 0;
        for (long t : nodeTimes.values()) {
            sum += t;
        }
        return sum / nodeTimes.size();
    }

    public java.util.Map<Integer, Long> computeAdjustments(java.util.Map<Integer, Long> nodeTimes, long avgTime) {
        java.util.Map<Integer, Long> adj = new java.util.HashMap<>();
        if (nodeTimes != null) {
            nodeTimes.forEach((id, t) -> adj.put(id, avgTime - t));
        }
        return adj;
    }
}
