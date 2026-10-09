package com.minicord.lb;

import java.util.List;

public interface LoadBalancerAlgorithm {
    BackendNode selectNode(List<BackendNode> healthyNodes);
    String getName();
}
