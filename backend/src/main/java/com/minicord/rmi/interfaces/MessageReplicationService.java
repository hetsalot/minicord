package com.minicord.rmi.interfaces;

import com.minicord.rmi.dto.MessageDTO;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * RMI interface for inter-server message replication.
 * DC Topic 1: Java RMI / RPC
 * DC Topic 5: Data Consistency
 */
public interface MessageReplicationService extends Remote {

    /**
     * Replicate a message to this server.
     * Called by the server that originally received the message.
     * @param message The message DTO to replicate
     * @return true if successfully stored
     */
    boolean replicateMessage(MessageDTO message) throws RemoteException;

    /**
     * Get all messages since a given Lamport timestamp.
     * Used for recovery / state catchup after a server comes back online.
     * @param sinceTimestamp The Lamport timestamp to start from
     * @return List of messages since that timestamp
     */
    List<MessageDTO> getMessagesSince(long sinceTimestamp) throws RemoteException;

    /**
     * Health check — returns true if this server is operational.
     */
    boolean healthCheck() throws RemoteException;

    /**
     * Get the total message count on this server.
     * Used for consistency monitoring.
     */
    int getMessageCount() throws RemoteException;
}
