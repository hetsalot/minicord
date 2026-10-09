package com.minicord.lb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round Robin load balancing algorithm.
 * Distributes requests sequentially across all healthy backend nodes.
 * DC Topic 7: Load Balancer
 */
public class RoundRobin implements LoadBalancerAlgorithm {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public BackendNode selectNode(List<BackendNode> healthyNodes) {
        if (healthyNodes == null || healthyNodes.isEmpty()) return null;
        int next = Math.abs(index.getAndIncrement() % healthyNodes.size());
        return healthyNodes.get(next);
    }

    @Override
    public String getName() {
        return "ROUND_ROBIN";
    }
}
