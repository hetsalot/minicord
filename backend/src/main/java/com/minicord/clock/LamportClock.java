package com.minicord.clock;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lamport Logical Clock implementation.
 * 
 * DC Topic: Clock Synchronization
 * 
 * Rules:
 * - On local event: increment by 1
 * - On send: increment by 1, attach timestamp
 * - On receive: clock = max(local, received) + 1
 * 
 * Thread-safe using AtomicLong (DC Topic: Multithreading)
 */
@Component
public class LamportClock {

    private final AtomicLong counter = new AtomicLong(0);

    /**
     * Increment clock for a local event.
     * @return the new clock value
     */
    public long tick() {
        return counter.incrementAndGet();
    }

    /**
     * Send event: increment and return timestamp to attach to message.
     * @return the Lamport timestamp for the outgoing message
     */
    public long send() {
        return counter.incrementAndGet();
    }

    /**
     * Receive event: update clock to max(local, received) + 1.
     * @param receivedTimestamp the Lamport timestamp from the incoming message
     * @return the new clock value after update
     */
    public long receive(long receivedTimestamp) {
        long updated;
        do {
            long current = counter.get();
            long newValue = Math.max(current, receivedTimestamp) + 1;
            if (counter.compareAndSet(current, newValue)) {
                updated = newValue;
                break;
            }
            // CAS failed, retry (another thread modified counter concurrently)
            updated = -1;
        } while (updated == -1);
        return updated;
    }

    /**
     * Get current clock value without incrementing.
     */
    public long getTime() {
        return counter.get();
    }

    /**
     * Force-set the clock (used for testing/demo).
     */
    public void setTime(long value) {
        counter.set(value);
    }

    @Override
    public String toString() {
        return "LamportClock{" + counter.get() + "}";
    }
}
