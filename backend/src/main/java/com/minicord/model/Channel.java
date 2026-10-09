package com.minicord.model;

/**
 * Channel within a server.
 */
public class Channel {
    private String id;
    private String serverId;
    private String name;
    private String createdAt;

    public Channel() {}

    public Channel(String id, String serverId, String name) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
