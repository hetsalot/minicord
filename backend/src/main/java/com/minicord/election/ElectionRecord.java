package com.minicord.election;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Record of a leader election execution, containing audit steps.
 * DC Topic 6: Leader Election Visualization
 */
public class ElectionRecord {
    public static class Step {
        private String time;
        private String action;
        private String result;

        public Step(String time, String action, String result) {
            this.time = time;
            this.action = action;
            this.result = result;
        }

        public String getTime() { return time; }
        public String getAction() { return action; }
        public String getResult() { return result; }
    }

    private long id;
    private String algorithm;
    private int oldCoordinator;
    private int newCoordinator;
    private int triggeredBy;
    private String timestamp;
    private long durationMs;
    private List<Step> steps = new ArrayList<>();

    public ElectionRecord() {
        this.timestamp = Instant.now().toString();
    }

    public ElectionRecord(long id, String algorithm, int oldCoordinator, int triggeredBy) {
        this.id = id;
        this.algorithm = algorithm;
        this.oldCoordinator = oldCoordinator;
        this.triggeredBy = triggeredBy;
        this.timestamp = Instant.now().toString();
    }

    public void addStep(String action, String result) {
        String t = Instant.now().toString();
        steps.add(new Step(t, action, result));
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public int getOldCoordinator() { return oldCoordinator; }
    public void setOldCoordinator(int oldCoordinator) { this.oldCoordinator = oldCoordinator; }
    public int getNewCoordinator() { return newCoordinator; }
    public void setNewCoordinator(int newCoordinator) { this.newCoordinator = newCoordinator; }
    public int getTriggeredBy() { return triggeredBy; }
    public void setTriggeredBy(int triggeredBy) { this.triggeredBy = triggeredBy; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public List<Step> getSteps() { return steps; }
    public void setSteps(List<Step> steps) { this.steps = steps; }
}
