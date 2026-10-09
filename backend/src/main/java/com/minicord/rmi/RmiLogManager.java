package com.minicord.rmi;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory ring buffer recording RMI calls for visualization and auditing.
 * DC Topic 1: Java RMI / RPC
 * DC Topic 2: Thread-safe data structure (ConcurrentLinkedDeque)
 */
@Component
public class RmiLogManager {

    public static class RmiLogRecord {
        private final String timestamp;
        private final String method;
        private final int source;
        private final int destination;
        private final String status;
        private final long durationMs;
        private final String payload;
        private final String error;

        public RmiLogRecord(String method, int source, int destination, String status,
                            long durationMs, String payload, String error) {
            this.timestamp = Instant.now().toString();
            this.method = method;
            this.source = source;
            this.destination = destination;
            this.status = status;
            this.durationMs = durationMs;
            this.payload = payload;
            this.error = error;
        }

        public String getTimestamp() { return timestamp; }
        public String getMethod() { return method; }
        public int getSource() { return source; }
        public int getDestination() { return destination; }
        public String getStatus() { return status; }
        public long getDurationMs() { return durationMs; }
        public String getPayload() { return payload; }
        public String getError() { return error; }
    }

    private static final int MAX_LOGS = 500;
    private final ConcurrentLinkedDeque<RmiLogRecord> logQueue = new ConcurrentLinkedDeque<>();

    public void log(String method, int source, int destination, String status,
                    long durationMs, String payload, String error) {
        logQueue.addFirst(new RmiLogRecord(method, source, destination, status, durationMs, payload, error));
        while (logQueue.size() > MAX_LOGS) {
            logQueue.pollLast();
        }
    }

    public List<RmiLogRecord> getRecentLogs(int limit, String methodFilter) {
        List<RmiLogRecord> result = new ArrayList<>();
        for (RmiLogRecord rec : logQueue) {
            if (methodFilter == null || methodFilter.isBlank() || rec.getMethod().equalsIgnoreCase(methodFilter)) {
                result.add(rec);
                if (result.size() >= limit) break;
            }
        }
        return result;
    }
}
