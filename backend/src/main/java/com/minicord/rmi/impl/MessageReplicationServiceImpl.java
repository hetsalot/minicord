package com.minicord.rmi.impl;

import com.minicord.clock.LamportClock;
import com.minicord.clock.VectorClock;
import com.minicord.model.Message;
import com.minicord.rmi.dto.MessageDTO;
import com.minicord.rmi.interfaces.MessageReplicationService;
import com.minicord.storage.MessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of MessageReplicationService.
 * DC Topic 1: Java RMI
 * DC Topic 3: Clock Synchronization (updates Lamport & Vector clocks on receive)
 * DC Topic 5: Data Consistency
 */
@Service
public class MessageReplicationServiceImpl extends UnicastRemoteObject implements MessageReplicationService {
    private static final Logger log = LoggerFactory.getLogger(MessageReplicationServiceImpl.class);

    private final MessageStore messageStore;
    private final LamportClock lamportClock;
    private final VectorClock vectorClock;

    private Consumer<Message> onMessageReplicatedCallback;

    public MessageReplicationServiceImpl(MessageStore messageStore, LamportClock lamportClock, VectorClock vectorClock) throws RemoteException {
        super();
        this.messageStore = messageStore;
        this.lamportClock = lamportClock;
        this.vectorClock = vectorClock;
    }

    public void setOnMessageReplicatedCallback(Consumer<Message> callback) {
        this.onMessageReplicatedCallback = callback;
    }

    @Override
    public boolean replicateMessage(MessageDTO dto) throws RemoteException {
        try {
            // Update logical clocks upon receiving message from another server
            lamportClock.receive(dto.getLamportTimestamp());
            if (dto.getVectorClock() != null) {
                vectorClock.merge(dto.getVectorClock());
            }

            Message msg = messageStore.fromDTO(dto);
            messageStore.saveMessage(msg);

            log.info("Replicated message [{}] from server {}", msg.getId(), msg.getServerOrigin());

            if (onMessageReplicatedCallback != null) {
                onMessageReplicatedCallback.accept(msg);
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to replicate message {}", dto.getId(), e);
            return false;
        }
    }

    @Override
    public List<MessageDTO> getMessagesSince(long sinceTimestamp) throws RemoteException {
        return messageStore.getMessagesSince(sinceTimestamp);
    }

    @Override
    public boolean healthCheck() throws RemoteException {
        return true;
    }

    @Override
    public int getMessageCount() throws RemoteException {
        return messageStore.getMessageCount();
    }
}
