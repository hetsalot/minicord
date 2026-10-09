package com.minicord.lb;

import java.util.Comparator;
import java.util.List;

/**
 * Least Connections load balancing algorithm.
 * Selects the healthy node with the lowest number of active in-flight connections.
 * DC Topic 7: Load Balancer
 */
public class LeastConnections implements LoadBalancerAlgorithm {

    @Override
    public BackendNode selectNode(List<BackendNode> healthyNodes) {
        if (healthyNodes == null || healthyNodes.isEmpty()) return null;
        return healthyNodes.stream()
                .min(Comparator.comparingInt(BackendNode::getActiveConnections))
                .orElse(healthyNodes.get(0));
    }

    @Override
    public String getName() {
        return "LEAST_CONNECTIONS";
    }
}
