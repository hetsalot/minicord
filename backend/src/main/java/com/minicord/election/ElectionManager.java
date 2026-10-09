package com.minicord.election;

import com.google.gson.Gson;
import com.minicord.config.ServerConfig;
import com.minicord.rmi.RmiClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Manager orchestrating election execution, storing history, and tracking active coordinator.
 * DC Topic 6: Bully & Ring Election
 */
@Component
public class ElectionManager {
    private static final Logger log = LoggerFactory.getLogger(ElectionManager.class);
    private static final Gson gson = new Gson();

    private final ServerConfig serverConfig;
    private final BullyAlgorithm bullyAlgorithm;
    private final RingAlgorithm ringAlgorithm;
    private final RmiClientManager rmiClientManager;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private volatile int currentCoordinator;
    private final AtomicLong electionIdGen = new AtomicLong(1);
    private final ConcurrentLinkedDeque<ElectionRecord> electionHistory = new ConcurrentLinkedDeque<>();
    private Consumer<ElectionRecord> onElectionCompleteCallback;

    public ElectionManager(ServerConfig serverConfig,
                           BullyAlgorithm bullyAlgorithm,
                           RingAlgorithm ringAlgorithm,
                           RmiClientManager rmiClientManager) {
        this.serverConfig = serverConfig;
        this.bullyAlgorithm = bullyAlgorithm;
        this.ringAlgorithm = ringAlgorithm;
        this.rmiClientManager = rmiClientManager;
        // By default initially, node with highest ID is coordinator
        this.currentCoordinator = serverConfig.getTotalNodes();
    }

    public void setOnElectionCompleteCallback(Consumer<ElectionRecord> callback) {
        this.onElectionCompleteCallback = callback;
    }

    public int getCurrentCoordinator() {
        return currentCoordinator;
    }

    public void setCurrentCoordinator(int coordinator) {
        log.info("Coordinator updated to Server {}", coordinator);
        this.currentCoordinator = coordinator;
    }

    public synchronized ElectionRecord triggerElection(String algorithmOverride) {
        String algo = (algorithmOverride != null && !algorithmOverride.isBlank())
                ? algorithmOverride.toUpperCase()
                : serverConfig.getElectionAlgorithm().toUpperCase();

        int oldCoord = currentCoordinator;
        int myId = serverConfig.getServerId();
        ElectionRecord record = new ElectionRecord(electionIdGen.getAndIncrement(), algo, oldCoord, myId);

        log.info("Triggering election using algorithm: {}", algo);

        int elected;
        if ("RING".equalsIgnoreCase(algo)) {
            elected = ringAlgorithm.runElection(record);
        } else {
            elected = bullyAlgorithm.runElection(record);
        }

        this.currentCoordinator = elected;
        electionHistory.addFirst(record);

        // Optional JDBC persistence to election_log table
        if (jdbcTemplate != null) {
            try {
                String sql = "INSERT INTO election_log (algorithm, old_coordinator, new_coordinator, triggered_by, duration_ms, details) " +
                        "VALUES (?, ?, ?, ?, ?, ?)";
                jdbcTemplate.update(sql, algo, oldCoord, elected, myId, (int) record.getDurationMs(), gson.toJson(record.getSteps()));
            } catch (Exception e) {
                log.debug("Could not persist election log to database: {}", e.getMessage());
            }
        }

        if (onElectionCompleteCallback != null) {
            onElectionCompleteCallback.accept(record);
        }

        return record;
    }

    public List<ElectionRecord> getElectionHistory(int limit) {
        List<ElectionRecord> list = new ArrayList<>();
        for (ElectionRecord r : electionHistory) {
            list.add(r);
            if (list.size() >= limit) break;
        }
        return list;
    }
}
