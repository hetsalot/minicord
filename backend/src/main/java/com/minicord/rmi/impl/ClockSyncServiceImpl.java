package com.minicord.rmi.impl;

import com.minicord.clock.BerkeleySync;
import com.minicord.clock.LamportClock;
import com.minicord.clock.VectorClock;
import com.minicord.rmi.interfaces.ClockSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * Implementation of ClockSyncService for RMI.
 * DC Topic 1: Java RMI
 * DC Topic 3: Clock Synchronization
 */
@Service
public class ClockSyncServiceImpl extends UnicastRemoteObject implements ClockSyncService {
    private static final Logger log = LoggerFactory.getLogger(ClockSyncServiceImpl.class);

    private final BerkeleySync berkeleySync;
    private final LamportClock lamportClock;
    private final VectorClock vectorClock;

    public ClockSyncServiceImpl(BerkeleySync berkeleySync, LamportClock lamportClock, VectorClock vectorClock) throws RemoteException {
        super();
        this.berkeleySync = berkeleySync;
        this.lamportClock = lamportClock;
        this.vectorClock = vectorClock;
    }

    @Override
    public long getLocalTime() throws RemoteException {
        return berkeleySync.getTime();
    }

    @Override
    public void adjustClock(long offsetMs) throws RemoteException {
        log.info("Clock adjusted by offset: {} ms", offsetMs);
        berkeleySync.adjustTime(offsetMs);
    }

    @Override
    public long getLamportClock() throws RemoteException {
        return lamportClock.getTime();
    }

    @Override
    public long[] getVectorClock() throws RemoteException {
        return vectorClock.getClock();
    }

    @Override
    public void setClockSkew(long offsetMs) throws RemoteException {
        log.info("Setting artificial clock skew: {} ms", offsetMs);
        berkeleySync.setClockOffset(offsetMs);
    }
}
