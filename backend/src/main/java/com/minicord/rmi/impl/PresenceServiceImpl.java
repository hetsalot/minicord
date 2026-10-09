package com.minicord.rmi.impl;

import com.minicord.rmi.interfaces.PresenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of PresenceService for cross-server presence synchronization.
 * DC Topic 1: Java RMI
 * DC Topic 2: Multithreading (ConcurrentHashMap)
 */
@Service
public class PresenceServiceImpl extends UnicastRemoteObject implements PresenceService {
    private static final Logger log = LoggerFactory.getLogger(PresenceServiceImpl.class);

    private final ConcurrentHashMap<String, String> userPresenceMap = new ConcurrentHashMap<>();

    public PresenceServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public void updatePresence(String userId, String status) throws RemoteException {
        log.debug("Updating presence for user {} -> {}", userId, status);
        userPresenceMap.put(userId, status);
    }

    @Override
    public Map<String, String> getAllPresence() throws RemoteException {
        return new HashMap<>(userPresenceMap);
    }

    public String getUserStatus(String userId) {
        return userPresenceMap.getOrDefault(userId, "offline");
    }
}
