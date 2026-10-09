package com.minicord.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Central configuration for this MiniCord server instance.
 * Reads from environment variables / application.properties.
 */
@Component
public class ServerConfig {

    @Value("${minicord.server.id}")
    private int serverId;

    @Value("${minicord.server.total-nodes}")
    private int totalNodes;

    @Value("${minicord.rmi.registry.host}")
    private String rmiRegistryHost;

    @Value("${minicord.rmi.registry.port}")
    private int rmiRegistryPort;

    @Value("${minicord.peers:}")
    private String peersString;

    @Value("${minicord.jwt.secret}")
    private String jwtSecret;

    @Value("${minicord.jwt.expiration}")
    private long jwtExpiration;

    @Value("${minicord.heartbeat.interval-ms}")
    private long heartbeatInterval;

    @Value("${minicord.heartbeat.timeout-ms}")
    private long heartbeatTimeout;

    @Value("${minicord.clock.sync-interval-ms}")
    private long clockSyncInterval;

    @Value("${minicord.consistency.mode}")
    private String consistencyMode;

    @Value("${minicord.consistency.quorum-w}")
    private int quorumW;

    @Value("${minicord.consistency.quorum-r}")
    private int quorumR;

    @Value("${minicord.election.algorithm}")
    private String electionAlgorithm;

    private List<String> peers = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (peersString != null && !peersString.isBlank()) {
            for (String peer : peersString.split(",")) {
                peers.add(peer.trim());
            }
        }
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║       MiniCord Server Starting           ║");
        System.out.println("║  Server ID:       " + serverId + "                       ║");
        System.out.println("║  Total Nodes:     " + totalNodes + "                       ║");
        System.out.println("║  RMI Registry:    " + rmiRegistryHost + ":" + rmiRegistryPort + "       ║");
        System.out.println("║  Election Algo:   " + electionAlgorithm + "                   ║");
        System.out.println("║  Consistency:     " + consistencyMode + "                ║");
        System.out.println("╚══════════════════════════════════════════╝");
    }

    // --- Getters ---
    public int getServerId() { return serverId; }
    public int getTotalNodes() { return totalNodes; }
    public String getRmiRegistryHost() { return rmiRegistryHost; }
    public int getRmiRegistryPort() { return rmiRegistryPort; }
    public List<String> getPeers() { return peers; }
    public String getJwtSecret() { return jwtSecret; }
    public long getJwtExpiration() { return jwtExpiration; }
    public long getHeartbeatInterval() { return heartbeatInterval; }
    public long getHeartbeatTimeout() { return heartbeatTimeout; }
    public long getClockSyncInterval() { return clockSyncInterval; }
    public String getConsistencyMode() { return consistencyMode; }
    public void setConsistencyMode(String mode) { this.consistencyMode = mode; }
    public int getQuorumW() { return quorumW; }
    public int getQuorumR() { return quorumR; }
    public String getElectionAlgorithm() { return electionAlgorithm; }
    public void setElectionAlgorithm(String algo) { this.electionAlgorithm = algo; }
}
