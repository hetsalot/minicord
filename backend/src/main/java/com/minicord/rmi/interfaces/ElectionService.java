package com.minicord.rmi.interfaces;

import com.minicord.rmi.dto.ElectionResponse;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * RMI interface for Bully and Ring Leader Election.
 * DC Topic 1: Java RMI / RPC
 * DC Topic 6: Bully and Ring Algorithms
 */
public interface ElectionService extends Remote {

    /**
     * Bully Algorithm: Receive an ELECTION message.
     * If this server's ID is higher, it responds OK and starts its own election.
     * @param candidateId The ID of the server calling the election
     * @return ElectionResponse with OK/REJECT
     */
    ElectionResponse election(int candidateId) throws RemoteException;

    /**
     * Receive a COORDINATOR announcement.
     * The sender is declaring itself as the new coordinator.
     * @param coordinatorId The ID of the new coordinator
     */
    void coordinator(int coordinatorId) throws RemoteException;

    /**
     * Ring Algorithm: Forward an election message around the ring.
     * @param participantIds List of server IDs visited so far
     * @param initiatorId The ID of the server that started the election
     * @return Updated list of participant IDs (with this server's ID added)
     */
    List<Integer> ringElection(List<Integer> participantIds, int initiatorId) throws RemoteException;

    /**
     * Check if this server is alive and responsive.
     */
    boolean isAlive() throws RemoteException;

    /**
     * Get this server's ID.
     */
    int getServerId() throws RemoteException;
}
