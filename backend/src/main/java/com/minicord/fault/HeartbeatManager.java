package com.minicord.fault;

import com.minicord.config.ServerConfig;
import com.minicord.election.ElectionManager;
import com.minicord.election.ElectionServiceImpl;
import com.minicord.rmi.RmiClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Tracks node heartbeats and detects node failures.
 * DC Topic 4: Fault Tolerance
 */
@Component
public class HeartbeatManager {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatManager.class);

    private final ServerConfig serverConfig;
    private final RmiClientManager rmiClientManager;
    private final ElectionManager electionManager;
    private final ElectionServiceImpl electionService;

    // Track last heartbeat received/observed per server ID
    private final ConcurrentHashMap<Integer, Long> lastSeenMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> nodeStatusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Integer> missedCounts = new ConcurrentHashMap<>();

    private BiConsumer<Integer, String> onNodeStatusChangedCallback;

    public HeartbeatManager(ServerConfig serverConfig,
                            RmiClientManager rmiClientManager,
                            @Lazy ElectionManager electionManager,
                            @Lazy ElectionServiceImpl electionService) {
        this.serverConfig = serverConfig;
        this.rmiClientManager = rmiClientManager;
        this.electionManager = electionManager;
        this.electionService = electionService;
    }

    public void setOnNodeStatusChangedCallback(BiConsumer<Integer, String> callback) {
        this.onNodeStatusChangedCallback = callback;
    }

    @PostConstruct
    public void init() {
        for (int i = 1; i <= serverConfig.getTotalNodes(); i++) {
            lastSeenMap.put(i, System.currentTimeMillis());
            nodeStatusMap.put(i, i == serverConfig.getServerId() ? "ALIVE" : "ALIVE");
            missedCounts.put(i, 0);
        }
    }

    /**
     * Heartbeat check runs every 3 seconds (Topic 4 requirement 8.1 & 8.2).
     */
    @Scheduled(fixedRate = 3000)
    public void checkHeartbeats() {
        if (electionService.isSimulatedKilled()) {
            return; // If this node is simulated killed, don't execute heartbeats
        }

        int myId = serverConfig.getServerId();
        int totalNodes = serverConfig.getTotalNodes();
        int coordinator = electionManager.getCurrentCoordinator();

        for (int peerId = 1; peerId <= totalNodes; peerId++) {
            if (peerId == myId) {
                lastSeenMap.put(myId, System.currentTimeMillis());
                nodeStatusMap.put(myId, "ALIVE");
                continue;
            }

            boolean alive = rmiClientManager.isNodeAlive(peerId);
            if (alive) {
                lastSeenMap.put(peerId, System.currentTimeMillis());
                missedCounts.put(peerId, 0);
                String oldStatus = nodeStatusMap.put(peerId, "ALIVE");
                if ("DEAD".equals(oldStatus)) {
                    log.info("Server {} has recovered to ALIVE state", peerId);
                    if (onNodeStatusChangedCallback != null) {
                        onNodeStatusChangedCallback.accept(peerId, "ALIVE");
                    }
                }
            } else {
                int misses = missedCounts.merge(peerId, 1, Integer::sum);
                // After 3 misses (9s), mark as DEAD (Topic 4 requirement 8.2.1)
                if (misses >= 3 && !"DEAD".equals(nodeStatusMap.get(peerId))) {
                    nodeStatusMap.put(peerId, "DEAD");
                    log.warn("Server {} marked as DEAD (missed {} heartbeats)", peerId, misses);

                    if (onNodeStatusChangedCallback != null) {
                        onNodeStatusChangedCallback.accept(peerId, "DEAD");
                    }

                    // If dead node was coordinator, trigger leader election!
                    if (peerId == coordinator) {
                        log.warn("Coordinator (Server {}) is DEAD! Triggering leader election...", coordinator);
                        new Thread(() -> electionManager.triggerElection(serverConfig.getElectionAlgorithm())).start();
                    }
                }
            }
        }
    }

    public List<Map<String, Object>> getNodeStatuses(int activeConnections) {
        List<Map<String, Object>> list = new ArrayList<>();
        int coordinator = electionManager.getCurrentCoordinator();
        int myId = serverConfig.getServerId();

        for (int i = 1; i <= serverConfig.getTotalNodes(); i++) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", i);
            node.put("name", "minicord-server-" + i);

            String status = nodeStatusMap.getOrDefault(i, "ALIVE");
            if (i == myId && electionService.isSimulatedKilled()) {
                status = "DEAD";
            }
            node.put("status", status);

            String role = (i == coordinator) ? "COORDINATOR" : ("DEAD".equals(status) ? "NONE" : "FOLLOWER");
            node.put("role", role);
            node.put("connections", (i == myId) ? activeConnections : 0);
            node.put("lastHeartbeat", Instant.ofEpochMilli(lastSeenMap.getOrDefault(i, System.currentTimeMillis())).toString());
            node.put("uptime", "DEAD".equals(status) ? "0s" : "online");
            list.add(node);
        }
        return list;
    }
}
