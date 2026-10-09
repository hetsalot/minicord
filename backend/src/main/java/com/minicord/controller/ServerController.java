package com.minicord.controller;

import com.minicord.model.Channel;
import com.minicord.model.Server;
import com.minicord.security.JwtUtil;
import com.minicord.storage.ServerStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class ServerController {

    private final ServerStore serverStore;
    private final JwtUtil jwtUtil;

    public ServerController(ServerStore serverStore, JwtUtil jwtUtil) {
        this.serverStore = serverStore;
        this.jwtUtil = jwtUtil;
    }

    private String getUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String uId = jwtUtil.getUserIdFromToken(token);
            if (uId != null) return uId;
        }
        return "anonymous";
    }

    @GetMapping("/api/servers")
    public ResponseEntity<ApiResponse<?>> listServers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String userId = getUserId(authHeader);
        List<Server> servers = serverStore.getServersForUser(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("servers", servers);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/api/servers")
    public ResponseEntity<ApiResponse<?>> createServer(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                       @RequestBody Map<String, String> body) {
        String userId = getUserId(authHeader);
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "Server name required"));
        }

        Server server = serverStore.createServer(name, userId);
        List<Channel> channels = serverStore.getChannelsForServer(server.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("id", server.getId());
        data.put("name", server.getName());
        data.put("ownerId", server.getOwnerId());
        data.put("inviteCode", server.getInviteCode());
        data.put("channels", channels);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    @PostMapping("/api/servers/join")
    public ResponseEntity<ApiResponse<?>> joinServer(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                     @RequestBody Map<String, String> body) {
        String userId = getUserId(authHeader);
        String inviteCode = body.get("inviteCode");
        Server s = serverStore.joinServerByInviteCode(inviteCode, userId);
        if (s == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND", "Invalid invite code"));
        }
        List<Channel> channels = serverStore.getChannelsForServer(s.getId());
        Map<String, Object> data = new HashMap<>();
        data.put("id", s.getId());
        data.put("name", s.getName());
        data.put("channels", channels);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @DeleteMapping("/api/servers/{serverId}")
    public ResponseEntity<ApiResponse<?>> deleteServer(@PathVariable("serverId") String serverId) {
        Map<String, String> data = new HashMap<>();
        data.put("message", "Server deleted");
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/api/servers/{serverId}/channels")
    public ResponseEntity<ApiResponse<?>> listChannels(@PathVariable("serverId") String serverId) {
        List<Channel> channels = serverStore.getChannelsForServer(serverId);
        Map<String, Object> data = new HashMap<>();
        data.put("channels", channels);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/api/servers/{serverId}/channels")
    public ResponseEntity<ApiResponse<?>> createChannel(@PathVariable("serverId") String serverId,
                                                        @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "Channel name required"));
        }
        Channel channel = serverStore.createChannel(serverId, name);
        Map<String, Object> data = new HashMap<>();
        data.put("id", channel.getId());
        data.put("serverId", channel.getServerId());
        data.put("name", channel.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    @DeleteMapping("/api/channels/{channelId}")
    public ResponseEntity<ApiResponse<?>> deleteChannel(@PathVariable("channelId") String channelId) {
        serverStore.deleteChannel(channelId);
        Map<String, String> data = new HashMap<>();
        data.put("message", "Channel deleted");
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
