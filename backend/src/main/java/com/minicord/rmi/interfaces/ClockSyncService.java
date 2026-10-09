package com.minicord.rmi.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * RMI interface for Berkeley Clock Synchronization & Logical Clocks.
 * DC Topic 1: Java RMI / RPC
 * DC Topic 3: Clock Synchronization
 */
public interface ClockSyncService extends Remote {

    /**
     * Get this server's current physical time (in milliseconds since epoch, including any skew).
     * Used by the Berkeley algorithm coordinator.
     */
    long getLocalTime() throws RemoteException;

    /**
     * Adjust this server's clock by the given offset.
     * Called by the Berkeley algorithm coordinator.
     * @param offsetMs Milliseconds to add (positive) or subtract (negative)
     */
    void adjustClock(long offsetMs) throws RemoteException;

    /**
     * Get this server's current Lamport clock value.
     */
    long getLamportClock() throws RemoteException;

    /**
     * Get this server's current vector clock.
     * @return Array of clock values, indexed by server ID
     */
    long[] getVectorClock() throws RemoteException;

    /**
     * Force set the clock offset (for demo simulation of clock skew).
     * @param offsetMs The offset to set in milliseconds
     */
    void setClockSkew(long offsetMs) throws RemoteException;
}
