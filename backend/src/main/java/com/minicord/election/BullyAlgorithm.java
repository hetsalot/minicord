package com.minicord.election;

import com.minicord.config.ServerConfig;
import com.minicord.rmi.RmiClientManager;
import com.minicord.rmi.RmiLogManager;
import com.minicord.rmi.dto.ElectionResponse;
import com.minicord.rmi.interfaces.ElectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * Bully Election Algorithm implementation.
 * DC Topic 6: Bully Algorithm
 * 
 * Logic:
 * 1. Node sends ELECTION message to all higher-ID nodes.
 * 2. If an OK response is received from any higher node, wait for coordinator message.
 * 3. If no OK response (or timeout), declare self as COORDINATOR and announce to all nodes.
 */
@Component
public class BullyAlgorithm {
    private static final Logger log = LoggerFactory.getLogger(BullyAlgorithm.class);

    private final ServerConfig serverConfig;
    private final RmiClientManager rmiClientManager;
    private final RmiLogManager rmiLogManager;

    public BullyAlgorithm(ServerConfig serverConfig, RmiClientManager rmiClientManager, RmiLogManager rmiLogManager) {
        this.serverConfig = serverConfig;
        this.rmiClientManager = rmiClientManager;
        this.rmiLogManager = rmiLogManager;
    }

    public int runElection(ElectionRecord record) {
        int myId = serverConfig.getServerId();
        int totalNodes = serverConfig.getTotalNodes();
        long startTime = System.currentTimeMillis();

        log.info("Starting Bully election triggered by Server {}", myId);
        record.addStep("Server " + myId + " starts Bully Election", "INITIATED");

        boolean higherNodeResponded = false;

        // 1. Send ELECTION to all higher-ID nodes
        for (int peerId = myId + 1; peerId <= totalNodes; peerId++) {
            long callStart = System.currentTimeMillis();
            try {
                record.addStep("Server " + myId + " sends ELECTION to Server " + peerId, "SENT");
                ElectionService service = rmiClientManager.getElectionService(peerId);
                if (service != null) {
                    ElectionResponse response = service.election(myId);
                    long duration = System.currentTimeMillis() - callStart;
                    rmiLogManager.log("election", myId, peerId, "SUCCESS", duration, "Bully:candidate=" + myId, null);

                    if (response != null && response.isOk()) {
                        record.addStep("Server " + peerId + " replied OK to Server " + myId, "OK");
                        higherNodeResponded = true;
                    }
                } else {
                    record.addStep("Server " + peerId + " unreachable (RMI lookup failed)", "FAILED");
                    rmiLogManager.log("election", myId, peerId, "FAILED", 0, "Lookup failed", "Not found");
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - callStart;
                record.addStep("Server " + myId + " -> Server " + peerId + " timeout / connection refused", "TIMEOUT");
                rmiLogManager.log("election", myId, peerId, "FAILED", duration, "ELECTION", e.getMessage());
            }
        }

        int newCoordinator;
        if (!higherNodeResponded) {
            // No higher node is alive -> I am the coordinator!
            newCoordinator = myId;
            record.addStep("No higher nodes responded. Server " + myId + " declares itself COORDINATOR", "VICTORY");
            announceCoordinatorToAll(newCoordinator, record);
        } else {
            // Higher node took over; wait a moment and verify highest alive
            record.addStep("Higher node responded. Server " + myId + " waiting for coordinator announcement", "WAITING");
            int highestAlive = myId;
            for (int i = totalNodes; i > myId; i--) {
                if (rmiClientManager.isNodeAlive(i)) {
                    highestAlive = i;
                    break;
                }
            }
            newCoordinator = highestAlive;
            record.addStep("Coordinator determined as Server " + newCoordinator, "RESOLVED");
        }

        record.setNewCoordinator(newCoordinator);
        record.setDurationMs(System.currentTimeMillis() - startTime);
        return newCoordinator;
    }

    public void announceCoordinatorToAll(int coordinatorId, ElectionRecord record) {
        int myId = serverConfig.getServerId();
        int totalNodes = serverConfig.getTotalNodes();

        for (int i = 1; i <= totalNodes; i++) {
            if (i == myId) continue;
            long callStart = System.currentTimeMillis();
            try {
                ElectionService service = rmiClientManager.getElectionService(i);
                if (service != null) {
                    service.coordinator(coordinatorId);
                    long duration = System.currentTimeMillis() - callStart;
                    record.addStep("Server " + myId + " announced COORDINATOR to Server " + i, "DELIVERED");
                    rmiLogManager.log("coordinator", myId, i, "SUCCESS", duration, "Coordinator=" + coordinatorId, null);
                }
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - callStart;
                record.addStep("Server " + myId + " announced COORDINATOR to Server " + i + " failed", "OFFLINE");
                rmiLogManager.log("coordinator", myId, i, "FAILED", duration, "Coordinator=" + coordinatorId, e.getMessage());
            }
        }
    }
}
