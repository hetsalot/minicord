package com.minicord.election;

import com.minicord.config.ServerConfig;
import com.minicord.rmi.RmiClientManager;
import com.minicord.rmi.RmiLogManager;
import com.minicord.rmi.interfaces.ElectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ring Election Algorithm implementation.
 * DC Topic 6: Ring Algorithm
 * 
 * Logic:
 * 1. Nodes are ordered in a logical ring: 1 -> 2 -> ... -> N -> 1.
 * 2. An initiator sends an ELECTION message containing its ID to its successor.
 * 3. Successors append their own IDs and forward around the ring. If a successor is dead, skip to next.
 * 4. When the token returns to the initiator, the highest ID is elected coordinator.
 * 5. Coordinator announcement circulates the ring.
 */
@Component
public class RingAlgorithm {
    private static final Logger log = LoggerFactory.getLogger(RingAlgorithm.class);

    private final ServerConfig serverConfig;
    private final RmiClientManager rmiClientManager;
    private final RmiLogManager rmiLogManager;

    public RingAlgorithm(ServerConfig serverConfig, RmiClientManager rmiClientManager, RmiLogManager rmiLogManager) {
        this.serverConfig = serverConfig;
        this.rmiClientManager = rmiClientManager;
        this.rmiLogManager = rmiLogManager;
    }

    public int runElection(ElectionRecord record) {
        int myId = serverConfig.getServerId();
        int totalNodes = serverConfig.getTotalNodes();
        long startTime = System.currentTimeMillis();

        log.info("Starting Ring election triggered by Server {}", myId);
        record.addStep("Server " + myId + " initiates Ring Election", "INITIATED");

        List<Integer> participants = new ArrayList<>();
        participants.add(myId);

        int current = myId;
        int visitedCount = 0;

        // Traverse ring until we return to initiator
        while (visitedCount < totalNodes) {
            int next = (current % totalNodes) + 1;
            if (next == myId) {
                record.addStep("Ring message returned to initiator Server " + myId, "RETURNED");
                break;
            }

            long callStart = System.currentTimeMillis();
            try {
                record.addStep("Server " + current + " forwards Ring Token " + participants + " to Server " + next, "FORWARD");
                ElectionService svc = rmiClientManager.getElectionService(next);
                if (svc != null && svc.isAlive()) {
                    participants = svc.ringElection(participants, myId);
                    long duration = System.currentTimeMillis() - callStart;
                    record.addStep("Server " + next + " added its ID to participants: " + participants, "ACK");
                    rmiLogManager.log("ringElection", current, next, "SUCCESS", duration, "Ring=" + participants, null);
                    current = next;
                } else {
                    record.addStep("Server " + next + " unreachable in ring, skipping to next neighbor", "BYPASS");
                    rmiLogManager.log("ringElection", current, next, "FAILED", 0, "Bypassed", "Offline");
                    current = next;
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - callStart;
                record.addStep("Server " + next + " failed during ring transmission: " + e.getMessage(), "TIMEOUT");
                rmiLogManager.log("ringElection", current, next, "FAILED", duration, "Timeout", e.getMessage());
                current = next;
            }
            visitedCount++;
        }

        // Pick highest ID as new coordinator
        int newCoordinator = participants.stream().max(Integer::compareTo).orElse(myId);
        record.addStep("Highest ID in ring participants " + participants + " is Server " + newCoordinator, "ELECTED");

        // Announce coordinator to all nodes
        for (int node : participants) {
            if (node == myId) continue;
            try {
                ElectionService svc = rmiClientManager.getElectionService(node);
                if (svc != null) {
                    svc.coordinator(newCoordinator);
                    record.addStep("Server " + myId + " announced COORDINATOR Server " + newCoordinator + " to Server " + node, "DELIVERED");
                }
            } catch (Exception ignored) {}
        }

        record.setNewCoordinator(newCoordinator);
        record.setDurationMs(System.currentTimeMillis() - startTime);
        return newCoordinator;
    }
}
