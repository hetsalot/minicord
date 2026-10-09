package com.minicord.lb;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Weighted Round Robin load balancing algorithm.
 * Selects nodes proportionally to their assigned weights.
 * DC Topic 7: Load Balancer
 */
public class WeightedRoundRobin implements LoadBalancerAlgorithm {
    private final AtomicInteger index = new AtomicInteger(0);

    @Override
    public BackendNode selectNode(List<BackendNode> healthyNodes) {
        if (healthyNodes == null || healthyNodes.isEmpty()) return null;

        List<BackendNode> weightedPool = new ArrayList<>();
        for (BackendNode n : healthyNodes) {
            int w = Math.max(1, n.getWeight());
            for (int i = 0; i < w; i++) {
                weightedPool.add(n);
            }
        }

        int next = Math.abs(index.getAndIncrement() % weightedPool.size());
        return weightedPool.get(next);
    }

    @Override
    public String getName() {
        return "WEIGHTED_ROUND_ROBIN";
    }
}
