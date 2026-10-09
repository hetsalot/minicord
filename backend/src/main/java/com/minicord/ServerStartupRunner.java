package com.minicord;

import com.minicord.election.ElectionManager;
import com.minicord.election.ElectionServiceImpl;
import com.minicord.fault.HeartbeatManager;
import com.minicord.fault.RecoveryManager;
import com.minicord.rmi.RmiClientManager;
import com.minicord.rmi.impl.MessageReplicationServiceImpl;
import com.minicord.threading.MessageProcessorThread;
import com.minicord.websocket.ChatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Initializes distributed bindings, registers callbacks, and performs state recovery.
 */
@Component
public class ServerStartupRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ServerStartupRunner.class);

    private final RmiClientManager rmiClientManager;
    private final ElectionServiceImpl electionService;
    private final ElectionManager electionManager;
    private final MessageProcessorThread messageProcessor;
    private final MessageReplicationServiceImpl replicationService;
    private final HeartbeatManager heartbeatManager;
    private final RecoveryManager recoveryManager;
    private final ChatWebSocketHandler wsHandler;

    public ServerStartupRunner(RmiClientManager rmiClientManager,
                               ElectionServiceImpl electionService,
                               ElectionManager electionManager,
                               MessageProcessorThread messageProcessor,
                               MessageReplicationServiceImpl replicationService,
                               HeartbeatManager heartbeatManager,
                               RecoveryManager recoveryManager,
                               ChatWebSocketHandler wsHandler) {
        this.rmiClientManager = rmiClientManager;
        this.electionService = electionService;
        this.electionManager = electionManager;
        this.messageProcessor = messageProcessor;
        this.replicationService = replicationService;
        this.heartbeatManager = heartbeatManager;
        this.recoveryManager = recoveryManager;
        this.wsHandler = wsHandler;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Wiring distributed event hooks and RMI registry bindings...");

        // Connect election service to RMI Manager
        rmiClientManager.setLocalElectionService(electionService);

        // When a replicated message is received from a peer, broadcast to our local WebSocket clients
        replicationService.setOnMessageReplicatedCallback(msg -> {
            wsHandler.broadcastMessage(msg);
        });

        // When a node status changes (e.g. ALIVE / DEAD), broadcast event to dashboard
        heartbeatManager.setOnNodeStatusChangedCallback((nodeId, status) -> {
            wsHandler.broadcastDashboardEvent("node_status_update", Map.of(
                    "nodeId", nodeId,
                    "status", status,
                    "reason", "Heartbeat status change"
            ));
        });

        // Try catching up on any missed messages from alive peers
        try {
            int recovered = recoveryManager.recoverState();
            log.info("Recovery complete on startup: {} messages synchronized", recovered);
        } catch (Exception e) {
            log.warn("Initial state recovery could not complete (cluster may still be starting): {}", e.getMessage());
        }

        log.info("MiniCord node startup routine complete.");
    }
}
