package com.minicord.fault;

import com.minicord.clock.LamportClock;
import com.minicord.clock.VectorClock;
import com.minicord.config.ServerConfig;
import com.minicord.model.Message;
import com.minicord.rmi.RmiClientManager;
import com.minicord.rmi.dto.MessageDTO;
import com.minicord.rmi.interfaces.MessageReplicationService;
import com.minicord.storage.MessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Recovers node state by syncing missed messages from alive peers.
 * DC Topic 4: Fault Tolerance (Recovery Manager)
 */
@Component
public class RecoveryManager {
    private static final Logger log = LoggerFactory.getLogger(RecoveryManager.class);

    private final ServerConfig serverConfig;
    private final MessageStore messageStore;
    private final LamportClock lamportClock;
    private final VectorClock vectorClock;
    private final RmiClientManager rmiClientManager;

    public RecoveryManager(ServerConfig serverConfig,
                           MessageStore messageStore,
                           LamportClock lamportClock,
                           VectorClock vectorClock,
                           RmiClientManager rmiClientManager) {
        this.serverConfig = serverConfig;
        this.messageStore = messageStore;
        this.lamportClock = lamportClock;
        this.vectorClock = vectorClock;
        this.rmiClientManager = rmiClientManager;
    }

    /**
     * Catch up missed messages from any alive peer server.
     */
    public int recoverState() {
        int myId = serverConfig.getServerId();
        int totalNodes = serverConfig.getTotalNodes();
        long lastKnownTs = lamportClock.getTime();
        int recoveredCount = 0;

        log.info("Server {} initiating recovery catchup from last timestamp: {}", myId, lastKnownTs);

        for (int peerId = 1; peerId <= totalNodes; peerId++) {
            if (peerId == myId) continue;
            try {
                MessageReplicationService svc = rmiClientManager.getReplicationService(peerId);
                if (svc != null && svc.healthCheck()) {
                    List<MessageDTO> missed = svc.getMessagesSince(lastKnownTs);
                    if (missed != null && !missed.isEmpty()) {
                        for (MessageDTO dto : missed) {
                            lamportClock.receive(dto.getLamportTimestamp());
                            if (dto.getVectorClock() != null) {
                                vectorClock.merge(dto.getVectorClock());
                            }
                            Message msg = messageStore.fromDTO(dto);
                            messageStore.saveMessage(msg);
                            recoveredCount++;
                        }
                        log.info("Server {} successfully caught up {} messages from Server {}", myId, missed.size(), peerId);
                        break; // Caught up from one reliable peer
                    }
                }
            } catch (Exception e) {
                log.debug("Could not recover from Server {}: {}", peerId, e.getMessage());
            }
        }

        // Re-register RMI services
        rmiClientManager.registerLocalServices();
        return recoveredCount;
    }
}
