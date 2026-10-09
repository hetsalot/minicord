package com.minicord.election;

import com.minicord.config.ServerConfig;
import com.minicord.rmi.dto.ElectionResponse;
import com.minicord.rmi.interfaces.ElectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of ElectionService for RMI calls.
 * DC Topic 1: Java RMI
 * DC Topic 6: Bully & Ring Algorithms
 */
@Service
public class ElectionServiceImpl extends UnicastRemoteObject implements ElectionService {
    private static final Logger log = LoggerFactory.getLogger(ElectionServiceImpl.class);

    private final ServerConfig serverConfig;
    private final ElectionManager electionManager;
    private volatile boolean simulatedKilled = false;

    public ElectionServiceImpl(ServerConfig serverConfig, @Lazy ElectionManager electionManager) throws RemoteException {
        super();
        this.serverConfig = serverConfig;
        this.electionManager = electionManager;
    }

    public boolean isSimulatedKilled() {
        return simulatedKilled;
    }

    public void setSimulatedKilled(boolean killed) {
        this.simulatedKilled = killed;
        log.info("Server {} simulated state set to killed={}", serverConfig.getServerId(), killed);
    }

    @Override
    public ElectionResponse election(int candidateId) throws RemoteException {
        if (simulatedKilled) {
            throw new RemoteException("Server " + serverConfig.getServerId() + " is currently simulated dead");
        }

        int myId = serverConfig.getServerId();
        log.info("Server {} received ELECTION from candidate {}", myId, candidateId);

        if (myId > candidateId) {
            // I have higher ID, take over by answering OK and kicking off an election
            new Thread(() -> electionManager.triggerElection("BULLY")).start();
            return new ElectionResponse(true, myId, "Server " + myId + " has higher ID");
        } else {
            return new ElectionResponse(false, myId, "Server " + myId + " has lower ID");
        }
    }

    @Override
    public void coordinator(int coordinatorId) throws RemoteException {
        log.info("Server {} acknowledges new COORDINATOR: {}", serverConfig.getServerId(), coordinatorId);
        electionManager.setCurrentCoordinator(coordinatorId);
    }

    @Override
    public List<Integer> ringElection(List<Integer> participantIds, int initiatorId) throws RemoteException {
        if (simulatedKilled) {
            throw new RemoteException("Server " + serverConfig.getServerId() + " is offline");
        }
        int myId = serverConfig.getServerId();
        List<Integer> updated = new ArrayList<>(participantIds);
        if (!updated.contains(myId)) {
            updated.add(myId);
        }
        log.info("Server {} added self to ring participants: {}", myId, updated);
        return updated;
    }

    @Override
    public boolean isAlive() throws RemoteException {
        if (simulatedKilled) {
            throw new RemoteException("Node simulated offline");
        }
        return true;
    }

    @Override
    public int getServerId() throws RemoteException {
        return serverConfig.getServerId();
    }
}
