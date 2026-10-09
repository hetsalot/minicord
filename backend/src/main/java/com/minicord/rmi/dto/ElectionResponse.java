package com.minicord.rmi.dto;

import java.io.Serializable;

/**
 * Response for leader election requests in Bully / Ring algorithm.
 * DC Topic 1 & 6: RMI + Bully/Ring Election
 */
public class ElectionResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean ok;          // true = "I have higher ID, I will take over"
    private int responderId;    // ID of node responding
    private String message;

    public ElectionResponse() {}

    public ElectionResponse(boolean ok, int responderId) {
        this.ok = ok;
        this.responderId = responderId;
    }

    public ElectionResponse(boolean ok, int responderId, String message) {
        this.ok = ok;
        this.responderId = responderId;
        this.message = message;
    }

    public boolean isOk() { return ok; }
    public void setOk(boolean ok) { this.ok = ok; }
    public int getResponderId() { return responderId; }
    public void setResponderId(int responderId) { this.responderId = responderId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
