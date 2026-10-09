package com.minicord.rmi.dto;

import java.io.Serializable;

/**
 * Serializable message representation for Java RMI transfer.
 * DC Topic 1: Java RMI / RPC
 */
public class MessageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String channelId;
    private String authorId;
    private String authorUsername;
    private String content;
    private long lamportTimestamp;
    private long[] vectorClock;
    private int serverOrigin;
    private long createdAt; // epoch millis

    public MessageDTO() {}

    public MessageDTO(String id, String channelId, String authorId, String authorUsername,
                      String content, long lamportTimestamp, long[] vectorClock,
                      int serverOrigin, long createdAt) {
        this.id = id;
        this.channelId = channelId;
        this.authorId = authorId;
        this.authorUsername = authorUsername;
        this.content = content;
        this.lamportTimestamp = lamportTimestamp;
        this.vectorClock = vectorClock;
        this.serverOrigin = serverOrigin;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public long getLamportTimestamp() { return lamportTimestamp; }
    public void setLamportTimestamp(long lamportTimestamp) { this.lamportTimestamp = lamportTimestamp; }
    public long[] getVectorClock() { return vectorClock; }
    public void setVectorClock(long[] vectorClock) { this.vectorClock = vectorClock; }
    public int getServerOrigin() { return serverOrigin; }
    public void setServerOrigin(int serverOrigin) { this.serverOrigin = serverOrigin; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
