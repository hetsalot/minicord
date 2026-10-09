package com.minicord.consistency;

import com.minicord.config.ServerConfig;
import com.minicord.model.Message;
import com.minicord.rmi.RmiClientManager;
import com.minicord.rmi.RmiLogManager;
import com.minicord.rmi.dto.MessageDTO;
import com.minicord.rmi.interfaces.MessageReplicationService;
import com.minicord.storage.MessageStore;
import com.minicord.threading.ReplicationThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages Strong, Eventual, and Quorum consistency models.
 * DC Topic 5: Data Consistency
 */
@Component
public class ConsistencyManager {
    private static final Logger log = LoggerFactory.getLogger(ConsistencyManager.class);

    private final ServerConfig serverConfig;
    private final MessageStore messageStore;
    private final RmiClientManager rmiClientManager;
    private final ReplicationThreadPool threadPool;
    private final RmiLogManager rmiLogManager;

    private final AtomicInteger conflictsDetected = new AtomicInteger(0);

    public ConsistencyManager(ServerConfig serverConfig,
                              MessageStore messageStore,
                              RmiClientManager rmiClientManager,
                              ReplicationThreadPool threadPool,
                              RmiLogManager rmiLogManager) {
        this.serverConfig = serverConfig;
        this.messageStore = messageStore;
        this.rmiClientManager = rmiClientManager;
        this.threadPool = threadPool;
        this.rmiLogManager = rmiLogManager;
    }

    /**
     * Replicates a message according to the currently active consistency mode.
     */
    public boolean replicate(Message message) {
        String mode = serverConfig.getConsistencyMode().toUpperCase();
        MessageDTO dto = messageStore.toDTO(message);

        switch (mode) {
            case "STRONG":
                return replicateStrong(dto);
            case "QUORUM":
                return replicateQuorum(dto);
            case "EVENTUAL":
            default:
                return replicateEventual(dto);
        }
    }

    /**
     * STRONG consistency: Synchronous replication to ALL alive peer servers.
     * Uses CountDownLatch to wait for all ACKs.
     */
    private boolean replicateStrong(MessageDTO dto) {
        int myId = serverConfig.getServerId();
        int totalNodes = serverConfig.getTotalNodes();
        List<Integer> peers = new ArrayList<>();
        for (int i = 1; i <= totalNodes; i++) {
            if (i != myId && rmiClientManager.isNodeAlive(i)) {
                peers.add(i);
            }
        }

        if (peers.isEmpty()) {
            return true;
        }

        CountDownLatch latch = new CountDownLatch(peers.size());
        AtomicInteger acks = new AtomicInteger(0);

        for (int peerId : peers) {
            final int target = peerId;
            threadPool.submit(() -> {
                long start = System.currentTimeMillis();
                try {
                    MessageReplicationService svc = rmiClientManager.getReplicationService(target);
                    if (svc != null && svc.replicateMessage(dto)) {
                        acks.incrementAndGet();
                        long duration = System.currentTimeMillis() - start;
                        rmiLogManager.log("replicateMessage", myId, target, "SUCCESS", duration, dto.getId(), null);
                    } else {
                        rmiLogManager.log("replicateMessage", myId, target, "FAILED", 0, dto.getId(), "Rejected");
                    }
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    rmiLogManager.log("replicateMessage", myId, target, "FAILED", duration, dto.getId(), e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            boolean done = latch.await(4, TimeUnit.SECONDS);
            return done && (acks.get() == peers.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * QUORUM consistency: Requires W ACKs (where W is configured, e.g. 2).
     */
    private boolean replicateQuorum(MessageDTO dto) {
        int myId = serverConfig.getServerId();
        int totalNodes = serverConfig.getTotalNodes();
        int requiredW = Math.min(serverConfig.getQuorumW(), totalNodes);

        // Local write counts as 1 ACK
        AtomicInteger acks = new AtomicInteger(1);
        CountDownLatch latch = new CountDownLatch(requiredW - 1);

        for (int peerId = 1; peerId <= totalNodes; peerId++) {
            if (peerId == myId) continue;
            final int target = peerId;
            threadPool.submit(() -> {
                long start = System.currentTimeMillis();
                try {
                    MessageReplicationService svc = rmiClientManager.getReplicationService(target);
                    if (svc != null && svc.replicateMessage(dto)) {
                        acks.incrementAndGet();
                        latch.countDown();
                        long duration = System.currentTimeMillis() - start;
                        rmiLogManager.log("replicateMessage", myId, target, "SUCCESS", duration, dto.getId(), null);
                    }
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    rmiLogManager.log("replicateMessage", myId, target, "FAILED", duration, dto.getId(), e.getMessage());
                }
            });
        }

        try {
            boolean achieved = latch.await(3, TimeUnit.SECONDS);
            return achieved || (acks.get() >= requiredW);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * EVENTUAL consistency: Asynchronous background replication via thread pool.
     */
    private boolean replicateEventual(MessageDTO dto) {
        int myId = serverConfig.getServerId();
        int totalNodes = serverConfig.getTotalNodes();

        for (int peerId = 1; peerId <= totalNodes; peerId++) {
            if (peerId == myId) continue;
            final int target = peerId;
            threadPool.submit(() -> {
                long start = System.currentTimeMillis();
                try {
                    MessageReplicationService svc = rmiClientManager.getReplicationService(target);
                    if (svc != null) {
                        svc.replicateMessage(dto);
                        long duration = System.currentTimeMillis() - start;
                        rmiLogManager.log("replicateMessage", myId, target, "SUCCESS", duration, dto.getId(), null);
                    }
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - start;
                    rmiLogManager.log("replicateMessage", myId, target, "FAILED", duration, dto.getId(), e.getMessage());
                }
            });
        }
        return true;
    }

    public Map<String, Object> getConsistencyStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("mode", serverConfig.getConsistencyMode());
        status.put("quorumW", serverConfig.getQuorumW());
        status.put("quorumR", serverConfig.getQuorumR());
        status.put("conflictsDetected", conflictsDetected.get());

        List<Map<String, Object>> serverStatusList = new ArrayList<>();
        int totalNodes = serverConfig.getTotalNodes();
        int localCount = messageStore.getMessageCount();

        Map<String, Object> replicationLag = new HashMap<>();

        for (int i = 1; i <= totalNodes; i++) {
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("id", i);
            int count = 0;
            if (i == serverConfig.getServerId()) {
                count = localCount;
            } else {
                try {
                    MessageReplicationService svc = rmiClientManager.getReplicationService(i);
                    if (svc != null) {
                        count = svc.getMessageCount();
                    }
                } catch (Exception ignored) {}
            }
            serverInfo.put("messageCount", count);
            serverInfo.put("latestMessageId", messageStore.getLatestMessage() != null ? messageStore.getLatestMessage().getId() : "none");
            serverStatusList.add(serverInfo);

            if (count < localCount) {
                Map<String, Object> lagInfo = new HashMap<>();
                lagInfo.put("behind", localCount - count);
                lagInfo.put("lagMs", (localCount - count) * 100L);
                replicationLag.put("server" + i, lagInfo);
            }
        }

        status.put("servers", serverStatusList);
        status.put("replicationLag", replicationLag);
        return status;
    }
}
