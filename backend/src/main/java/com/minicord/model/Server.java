package com.minicord.model;

/**
 * Server (guild) model.
 */
public class Server {
    private String id;
    private String name;
    private String ownerId;
    private String inviteCode;
    private int memberCount;
    private String createdAt;

    public Server() {}

    public Server(String id, String name, String ownerId, String inviteCode) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.inviteCode = inviteCode;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public int getMemberCount() { return memberCount; }
    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
