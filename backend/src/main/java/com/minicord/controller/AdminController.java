package com.minicord.controller;

import com.minicord.clock.BerkeleySync;
import com.minicord.clock.LamportClock;
import com.minicord.clock.VectorClock;
import com.minicord.config.ServerConfig;
import com.minicord.consistency.ConsistencyManager;
import com.minicord.election.ElectionManager;
import com.minicord.election.ElectionRecord;
import com.minicord.election.ElectionServiceImpl;
import com.minicord.fault.HeartbeatManager;
import com.minicord.fault.RecoveryManager;
import com.minicord.rmi.RmiClientManager;
import com.minicord.rmi.RmiLogManager;
import com.minicord.rmi.interfaces.ClockSyncService;
import com.minicord.threading.ThreadMonitorService;
import com.minicord.websocket.ChatWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class AdminController {

    private final ServerConfig serverConfig;
    private final HeartbeatManager heartbeatManager;
    private final ElectionManager electionManager;
    private final ElectionServiceImpl electionService;
    private final BerkeleySync berkeleySync;
    private final LamportClock lamportClock;
    private final VectorClock vectorClock;
    private final ConsistencyManager consistencyManager;
    private final ThreadMonitorService threadMonitorService;
    private final RmiLogManager rmiLogManager;
    private final RmiClientManager rmiClientManager;
    private final RecoveryManager recoveryManager;
    private final ChatWebSocketHandler wsHandler;

    public AdminController(ServerConfig serverConfig,
                           HeartbeatManager heartbeatManager,
                           ElectionManager electionManager,
                           ElectionServiceImpl electionService,
                           BerkeleySync berkeleySync,
                           LamportClock lamportClock,
                           VectorClock vectorClock,
                           ConsistencyManager consistencyManager,
                           ThreadMonitorService threadMonitorService,
                           RmiLogManager rmiLogManager,
                           RmiClientManager rmiClientManager,
                           RecoveryManager recoveryManager,
                           ChatWebSocketHandler wsHandler) {
        this.serverConfig = serverConfig;
        this.heartbeatManager = heartbeatManager;
        this.electionManager = electionManager;
        this.electionService = electionService;
        this.berkeleySync = berkeleySync;
        this.lamportClock = lamportClock;
        this.vectorClock = vectorClock;
        this.consistencyManager = consistencyManager;
        this.threadMonitorService = threadMonitorService;
        this.rmiLogManager = rmiLogManager;
        this.rmiClientManager = rmiClientManager;
        this.recoveryManager = recoveryManager;
        this.wsHandler = wsHandler;
    }

    // Health check endpoint for Load Balancer and health probing
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        if (electionService.isSimulatedKilled()) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "DOWN");
            err.put("serverId", serverConfig.getServerId());
            err.put("error", "Simulated crash (node is down)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(err);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("serverId", serverConfig.getServerId());
        data.put("role", serverConfig.getServerId() == electionManager.getCurrentCoordinator() ? "COORDINATOR" : "FOLLOWER");
        data.put("uptime", "online");
        data.put("connections", wsHandler.getActiveConnectionsCount());
        data.put("lamportClock", lamportClock.getTime());
        data.put("dbConnected", true);
        return ResponseEntity.ok(data);
    }

    // --- Topic 4: Fault Tolerance Nodes ---
    @GetMapping("/api/admin/nodes")
    public ResponseEntity<ApiResponse<?>> getNodes() {
        List<Map<String, Object>> nodes = heartbeatManager.getNodeStatuses(wsHandler.getActiveConnectionsCount());
        Map<String, Object> data = new HashMap<>();
        data.put("nodes", nodes);
        data.put("coordinator", electionManager.getCurrentCoordinator());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/api/admin/node/{nodeId}/kill")
    public ResponseEntity<ApiResponse<?>> killNode(@PathVariable("nodeId") int nodeId) {
        if (nodeId == serverConfig.getServerId()) {
            electionService.setSimulatedKilled(true);
        }
        Map<String, String> data = new HashMap<>();
        data.put("message", "Server " + nodeId + " killed");
        wsHandler.broadcastDashboardEvent("node_status_update", Map.of(
                "nodeId", nodeId,
                "status", "DEAD",
                "reason", "Simulated node termination via admin panel"
        ));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/api/admin/node/{nodeId}/revive")
    public ResponseEntity<ApiResponse<?>> reviveNode(@PathVariable("nodeId") int nodeId) {
        if (nodeId == serverConfig.getServerId()) {
            electionService.setSimulatedKilled(false);
            recoveryManager.recoverState();
        }
        Map<String, String> data = new HashMap<>();
        data.put("message", "Server " + nodeId + " revived");
        wsHandler.broadcastDashboardEvent("node_status_update", Map.of(
                "nodeId", nodeId,
                "status", "ALIVE",
                "reason", "Server revived via admin panel"
        ));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // --- Topic 3: Clock Synchronization ---
    @GetMapping("/api/admin/clocks")
    public ResponseEntity<ApiResponse<?>> getClocks() {
        List<Map<String, Object>> clocks = new ArrayList<>();
        int total = serverConfig.getTotalNodes();

        for (int i = 1; i <= total; i++) {
            Map<String, Object> clk = new HashMap<>();
            clk.put("serverId", i);
            if (i == serverConfig.getServerId()) {
                clk.put("lamportClock", lamportClock.getTime());
                clk.put("vectorClock", vectorClock.getClock());
                clk.put("physicalTime", Instant.ofEpochMilli(berkeleySync.getTime()).toString());
                clk.put("clockOffset", berkeleySync.getClockOffset());
            } else {
                try {
                    ClockSyncService svc = rmiClientManager.getClockSyncService(i);
                    if (svc != null) {
                        clk.put("lamportClock", svc.getLamportClock());
                        clk.put("vectorClock", svc.getVectorClock());
                        clk.put("physicalTime", Instant.ofEpochMilli(svc.getLocalTime()).toString());
                        clk.put("clockOffset", 0);
                    }
                } catch (Exception e) {
                    clk.put("lamportClock", 0);
                    clk.put("vectorClock", new long[total]);
                    clk.put("physicalTime", "OFFLINE");
                    clk.put("clockOffset", 0);
                }
            }
            clocks.add(clk);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("clocks", clocks);
        data.put("lastSyncTime", Instant.now().toString());
        data.put("syncAlgorithm", "BERKELEY");
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/api/admin/clock/skew")
    public ResponseEntity<ApiResponse<?>> setClockSkew(@RequestBody Map<String, Object> body) {
        int targetId = ((Number) body.get("serverId")).intValue();
        long offsetMs = ((Number) body.get("offsetMs")).longValue();

        if (targetId == serverConfig.getServerId()) {
            berkeleySync.setClockOffset(offsetMs);
        } else {
            try {
                ClockSyncService svc = rmiClientManager.getClockSyncService(targetId);
                if (svc != null) {
                    svc.setClockSkew(offsetMs);
                }
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.error("RMI_ERROR", e.getMessage()));
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("serverId", targetId);
        data.put("offsetMs", offsetMs);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/api/admin/clock/sync")
    public ResponseEntity<ApiResponse<?>> syncClocks() {
        int total = serverConfig.getTotalNodes();
        Map<Integer, Long> nodeTimes = new HashMap<>();
        nodeTimes.put(serverConfig.getServerId(), berkeleySync.getTime());

        for (int i = 1; i <= total; i++) {
            if (i == serverConfig.getServerId()) continue;
            try {
                ClockSyncService svc = rmiClientManager.getClockSyncService(i);
                if (svc != null) {
                    nodeTimes.put(i, svc.getLocalTime());
                }
            } catch (Exception ignored) {}
        }

        long avgTime = berkeleySync.computeAverageTime(nodeTimes);
        Map<Integer, Long> adjustments = berkeleySync.computeAdjustments(nodeTimes, avgTime);

        List<Map<String, Object>> adjustmentsList = new ArrayList<>();
        adjustments.forEach((nodeId, adj) -> {
            if (nodeId == serverConfig.getServerId()) {
                berkeleySync.adjustTime(adj);
            } else {
                try {
                    ClockSyncService svc = rmiClientManager.getClockSyncService(nodeId);
                    if (svc != null) svc.adjustClock(adj);
                } catch (Exception ignored) {}
            }
            adjustmentsList.add(Map.of("serverId", nodeId, "offsetMs", adj));
        });

        Map<String, Object> data = new HashMap<>();
        data.put("adjustments", adjustmentsList);
        data.put("averageTime", Instant.ofEpochMilli(avgTime).toString());
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    // --- Topic 6: Bully & Ring Leader Election ---
    @GetMapping("/api/admin/election/history")
    public ResponseEntity<ApiResponse<?>> getElectionHistory() {
        List<ElectionRecord> list = electionManager.getElectionHistory(50);
        Map<String, Object> data = new HashMap<>();
        data.put("elections", list);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/api/admin/election/trigger")
    public ResponseEntity<ApiResponse<?>> triggerElection(@RequestBody(required = false) Map<String, String> body) {
        String algo = body != null ? body.get("algorithm") : null;
        ElectionRecord record = electionManager.triggerElection(algo);

        wsHandler.broadcastDashboardEvent("election_complete", Map.of(
                "algorithm", record.getAlgorithm(),
                "newCoordinator", record.getNewCoordinator(),
                "oldCoordinator", record.getOldCoordinator(),
                "durationMs", record.getDurationMs()
        ));

        return ResponseEntity.ok(ApiResponse.ok(record));
    }

    // --- Topic 5: Data Consistency ---
    @PostMapping("/api/admin/consistency/mode")
    public ResponseEntity<ApiResponse<?>> setConsistencyMode(@RequestBody Map<String, Object> body) {
        String mode = (String) body.get("mode");
        if (mode != null) {
            serverConfig.setConsistencyMode(mode.toUpperCase());
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("mode", serverConfig.getConsistencyMode())));
    }

    @GetMapping("/api/admin/consistency/status")
    public ResponseEntity<ApiResponse<?>> getConsistencyStatus() {
        return ResponseEntity.ok(ApiResponse.ok(consistencyManager.getConsistencyStatus()));
    }

    // --- Topic 2: Multithreading & Thread Monitor ---
    @GetMapping("/api/admin/threads/{serverId}")
    public ResponseEntity<ApiResponse<?>> getThreads(@PathVariable("serverId") int targetServerId) {
        return ResponseEntity.ok(ApiResponse.ok(threadMonitorService.getThreadDump()));
    }

    // --- Topic 1: Java RMI Call Logs ---
    @GetMapping("/api/admin/rmi/logs")
    public ResponseEntity<ApiResponse<?>> getRmiLogs(@RequestParam(value = "limit", defaultValue = "50") int limit,
                                                    @RequestParam(value = "method", required = false) String method) {
        Map<String, Object> data = new HashMap<>();
        data.put("logs", rmiLogManager.getRecentLogs(limit, method));
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
