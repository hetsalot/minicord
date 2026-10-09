package com.minicord.lb;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Representation of a backend server node managed by the Load Balancer.
 * DC Topic 7: Load Balancer
 */
public class BackendNode {
    private final int id;
    private final String name;
    private final String host;
    private final int port;
    private final int weight;

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);

    private volatile String status = "ACTIVE"; // ACTIVE or DOWN
    private volatile String healthCheckStatus = "HEALTHY"; // HEALTHY or UNHEALTHY
    private volatile int failedChecks = 0;
    private volatile String lastHealthCheck = Instant.now().toString();

    public BackendNode(int id, String name, String host, int port, int weight) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.weight = weight;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getWeight() { return weight; }

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public void incrementConnections() {
        activeConnections.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void decrementConnections(long durationMs) {
        activeConnections.decrementAndGet();
        totalResponseTimeMs.addAndGet(durationMs);
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public long getAvgResponseTimeMs() {
        long reqs = totalRequests.get();
        return reqs > 0 ? (totalResponseTimeMs.get() / reqs) : 0;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getHealthCheckStatus() { return healthCheckStatus; }
    public void setHealthCheckStatus(String healthCheckStatus) { this.healthCheckStatus = healthCheckStatus; }

    public int getFailedChecks() { return failedChecks; }
    public void setFailedChecks(int failedChecks) { this.failedChecks = failedChecks; }

    public String getLastHealthCheck() { return lastHealthCheck; }
    public void setLastHealthCheck(String lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }

    public boolean isHealthy() {
        return "ACTIVE".equals(status) && "HEALTHY".equals(healthCheckStatus);
    }
}
