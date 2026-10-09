package com.minicord.rmi;

import com.minicord.config.ServerConfig;
import com.minicord.rmi.impl.ClockSyncServiceImpl;
import com.minicord.rmi.impl.MessageReplicationServiceImpl;
import com.minicord.rmi.impl.PresenceServiceImpl;
import com.minicord.rmi.interfaces.ClockSyncService;
import com.minicord.rmi.interfaces.ElectionService;
import com.minicord.rmi.interfaces.MessageReplicationService;
import com.minicord.rmi.interfaces.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages RMI Registry connections, binding local services and resolving remote peer stubs.
 * DC Topic 1: Java RMI / RPC
 * DC Topic 4: Fault Tolerance (handles unreachable nodes cleanly)
 */
@Component
public class RmiClientManager {
    private static final Logger log = LoggerFactory.getLogger(RmiClientManager.class);

    private final ServerConfig serverConfig;
    private final MessageReplicationServiceImpl replicationService;
    private final PresenceServiceImpl presenceService;
    private final ClockSyncServiceImpl clockSyncService;
    private final RmiLogManager rmiLogManager;

    private ElectionService localElectionService; // injected lazily to avoid circular dep
    private Registry registry;

    // Cache of remote stubs
    private final ConcurrentHashMap<String, Object> stubCache = new ConcurrentHashMap<>();

    public RmiClientManager(ServerConfig serverConfig,
                            MessageReplicationServiceImpl replicationService,
                            PresenceServiceImpl presenceService,
                            ClockSyncServiceImpl clockSyncService,
                            RmiLogManager rmiLogManager) {
        this.serverConfig = serverConfig;
        this.replicationService = replicationService;
        this.presenceService = presenceService;
        this.clockSyncService = clockSyncService;
        this.rmiLogManager = rmiLogManager;
    }

    public void setLocalElectionService(ElectionService electionService) {
        this.localElectionService = electionService;
        registerLocalServices();
    }

    @PostConstruct
    public void init() {
        try {
            int port = serverConfig.getRmiRegistryPort();
            String host = serverConfig.getRmiRegistryHost();
            try {
                // Try to create in-process registry if running locally and none exists
                registry = LocateRegistry.createRegistry(port);
                log.info("Created local RMI Registry on port {}", port);
            } catch (Exception e) {
                // Registry already exists (or running as separate container)
                registry = LocateRegistry.getRegistry(host, port);
                log.info("Connected to existing RMI Registry at {}:{}", host, port);
            }
        } catch (Exception e) {
            log.warn("Failed to initialize RMI registry connection: {}", e.getMessage());
        }
    }

    public synchronized void registerLocalServices() {
        if (registry == null) {
            init();
        }
        if (registry == null) return;

        int id = serverConfig.getServerId();
        try {
            registry.rebind("MessageReplicationService_" + id, replicationService);
            registry.rebind("PresenceService_" + id, presenceService);
            registry.rebind("ClockSyncService_" + id, clockSyncService);
            if (localElectionService != null) {
                registry.rebind("ElectionService_" + id, localElectionService);
            }
            log.info("Bound local RMI services for Server ID {} successfully", id);
        } catch (Exception e) {
            log.warn("Failed to bind RMI services for server {}: {}", id, e.getMessage());
        }
    }

    public MessageReplicationService getReplicationService(int targetId) {
        if (targetId == serverConfig.getServerId()) return replicationService;
        return (MessageReplicationService) lookupStub("MessageReplicationService_" + targetId);
    }

    public PresenceService getPresenceService(int targetId) {
        if (targetId == serverConfig.getServerId()) return presenceService;
        return (PresenceService) lookupStub("PresenceService_" + targetId);
    }

    public ElectionService getElectionService(int targetId) {
        if (targetId == serverConfig.getServerId()) return localElectionService;
        return (ElectionService) lookupStub("ElectionService_" + targetId);
    }

    public ClockSyncService getClockSyncService(int targetId) {
        if (targetId == serverConfig.getServerId()) return clockSyncService;
        return (ClockSyncService) lookupStub("ClockSyncService_" + targetId);
    }

    private Object lookupStub(String serviceName) {
        try {
            if (registry == null) init();
            if (registry == null) return null;
            return registry.lookup(serviceName);
        } catch (Exception e) {
            log.debug("RMI stub lookup failed for {}: {}", serviceName, e.getMessage());
            return null;
        }
    }

    public boolean isNodeAlive(int serverId) {
        if (serverId == serverConfig.getServerId()) return true;
        try {
            ElectionService svc = getElectionService(serverId);
            if (svc != null) {
                return svc.isAlive();
            }
        } catch (Exception ignored) {}
        return false;
    }
}
