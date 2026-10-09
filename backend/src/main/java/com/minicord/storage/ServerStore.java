package com.minicord.storage;

import com.minicord.model.Channel;
import com.minicord.model.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Stores servers, channels, and membership.
 */
@Component
public class ServerStore {
    private static final Logger log = LoggerFactory.getLogger(ServerStore.class);

    private final ConcurrentHashMap<String, Server> servers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Channel> channels = new ConcurrentHashMap<>();
    // serverId -> Set of userIds
    private final ConcurrentHashMap<String, Set<String>> serverMembers = new ConcurrentHashMap<>();
    // inviteCode -> serverId
    private final ConcurrentHashMap<String, String> inviteMap = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        // Seed default demo server
        String defaultServerId = "server-demo-1";
        Server s = new Server(defaultServerId, "DC MiniCord Cluster", "system", "dc2026");
        s.setCreatedAt(Instant.now().toString());
        servers.put(defaultServerId, s);
        inviteMap.put("dc2026", defaultServerId);

        // Seed default channels
        createChannelInternal(defaultServerId, "general", "chan-demo-gen");
        createChannelInternal(defaultServerId, "lamport-vector-clocks", "chan-demo-clk");
        createChannelInternal(defaultServerId, "bully-ring-elections", "chan-demo-elec");
        createChannelInternal(defaultServerId, "consistency-benchmarks", "chan-demo-cons");

        // Add dummy demo users
        serverMembers.computeIfAbsent(defaultServerId, k -> ConcurrentHashMap.newKeySet())
                .addAll(Arrays.asList("system", "all"));
    }

    private Channel createChannelInternal(String serverId, String name, String id) {
        Channel c = new Channel(id != null ? id : UUID.randomUUID().toString(), serverId, name);
        c.setCreatedAt(Instant.now().toString());
        channels.put(c.getId(), c);
        return c;
    }

    public Server createServer(String name, String ownerId) {
        String serverId = UUID.randomUUID().toString();
        String inviteCode = UUID.randomUUID().toString().substring(0, 6);

        Server s = new Server(serverId, name, ownerId, inviteCode);
        s.setCreatedAt(Instant.now().toString());
        s.setMemberCount(1);
        servers.put(serverId, s);
        inviteMap.put(inviteCode.toLowerCase(), serverId);

        serverMembers.computeIfAbsent(serverId, k -> ConcurrentHashMap.newKeySet()).add(ownerId);

        // Every server gets a default #general channel
        createChannelInternal(serverId, "general", null);

        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("INSERT INTO servers (id, name, owner_id, invite_code) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                        serverId, name, ownerId, inviteCode);
                jdbcTemplate.update("INSERT INTO server_members (user_id, server_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                        ownerId, serverId);
            } catch (Exception e) {
                log.debug("DB insert server skipped: {}", e.getMessage());
            }
        }

        return s;
    }

    public List<Server> getServersForUser(String userId) {
        List<Server> list = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : serverMembers.entrySet()) {
            if (entry.getValue().contains(userId) || entry.getValue().contains("all") || "server-demo-1".equals(entry.getKey())) {
                Server s = servers.get(entry.getKey());
                if (s != null) {
                    s.setMemberCount(entry.getValue().size());
                    list.add(s);
                }
            }
        }
        return list;
    }

    public Server joinServerByInviteCode(String inviteCode, String userId) {
        if (inviteCode == null) return null;
        String serverId = inviteMap.get(inviteCode.toLowerCase().trim());
        if (serverId == null) return null;

        serverMembers.computeIfAbsent(serverId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        Server s = servers.get(serverId);
        if (s != null) {
            s.setMemberCount(serverMembers.get(serverId).size());
        }
        return s;
    }

    public List<Channel> getChannelsForServer(String serverId) {
        return channels.values().stream()
                .filter(c -> serverId.equals(c.getServerId()))
                .collect(Collectors.toList());
    }

    public Channel createChannel(String serverId, String name) {
        Channel c = createChannelInternal(serverId, name, null);
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("INSERT INTO channels (id, server_id, name) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                        c.getId(), c.getServerId(), c.getName());
            } catch (Exception e) {
                log.debug("DB channel insert skipped: {}", e.getMessage());
            }
        }
        return c;
    }

    public boolean deleteChannel(String channelId) {
        Channel removed = channels.remove(channelId);
        return removed != null;
    }

    public Server getServerById(String serverId) {
        return servers.get(serverId);
    }
}
