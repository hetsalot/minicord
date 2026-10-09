package com.minicord.rmi.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

/**
 * RMI interface for cross-server presence synchronization.
 * DC Topic 1: Java RMI / RPC
 */
public interface PresenceService extends Remote {

    /**
     * Update a user's presence status on this server.
     * @param userId The user ID
     * @param status "online", "offline", "idle"
     */
    void updatePresence(String userId, String status) throws RemoteException;

    /**
     * Get all user presence states from this server.
     * @return Map of userId -> status
     */
    Map<String, String> getAllPresence() throws RemoteException;
}
