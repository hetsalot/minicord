package com.minicord.controller;

import com.minicord.clock.LamportClock;
import com.minicord.clock.VectorClock;
import com.minicord.config.ServerConfig;
import com.minicord.consistency.ConsistencyManager;
import com.minicord.model.Message;
import com.minicord.model.User;
import com.minicord.security.JwtUtil;
import com.minicord.storage.MessageStore;
import com.minicord.storage.UserStore;
import com.minicord.threading.MessageProcessorThread;
import com.minicord.websocket.ChatWebSocketHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class MessageController {

    private final MessageStore messageStore;
    private final LamportClock lamportClock;
    private final VectorClock vectorClock;
    private final ServerConfig serverConfig;
    private final JwtUtil jwtUtil;
    private final UserStore userStore;
    private final MessageProcessorThread messageProcessor;
    private final ConsistencyManager consistencyManager;
    private final ChatWebSocketHandler wsHandler;

    public MessageController(MessageStore messageStore,
                             LamportClock lamportClock,
                             VectorClock vectorClock,
                             ServerConfig serverConfig,
                             JwtUtil jwtUtil,
                             UserStore userStore,
                             MessageProcessorThread messageProcessor,
                             ConsistencyManager consistencyManager,
                             ChatWebSocketHandler wsHandler) {
        this.messageStore = messageStore;
        this.lamportClock = lamportClock;
        this.vectorClock = vectorClock;
        this.serverConfig = serverConfig;
        this.jwtUtil = jwtUtil;
        this.userStore = userStore;
        this.messageProcessor = messageProcessor;
        this.consistencyManager = consistencyManager;
        this.wsHandler = wsHandler;
    }

    private String getUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String uId = jwtUtil.getUserIdFromToken(token);
            if (uId != null) return uId;
        }
        return "anonymous";
    }

    @GetMapping("/api/channels/{channelId}/messages")
    public ResponseEntity<ApiResponse<?>> getMessages(@PathVariable("channelId") String channelId,
                                                      @RequestParam(value = "limit", defaultValue = "50") int limit,
                                                      @RequestParam(value = "before", required = false) String before) {
        List<Message> msgs = messageStore.getMessagesByChannel(channelId, limit, before);

        List<Map<String, Object>> formatted = new ArrayList<>();
        for (Message m : msgs) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("channelId", m.getChannelId());

            Map<String, Object> author = new HashMap<>();
            author.put("id", m.getAuthorId());
            author.put("username", m.getAuthorUsername() != null ? m.getAuthorUsername() : "User");
            author.put("avatarUrl", null);
            map.put("author", author);

            map.put("content", m.getContent());
            map.put("lamportTimestamp", m.getLamportTimestamp());
            map.put("vectorClock", m.getVectorClock());
            map.put("serverOrigin", m.getServerOrigin());
            map.put("createdAt", m.getCreatedAt());
            formatted.add(map);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("messages", formatted);
        data.put("hasMore", false);

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping("/api/channels/{channelId}/messages")
    public ResponseEntity<ApiResponse<?>> sendMessage(@PathVariable("channelId") String channelId,
                                                      @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                      @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "Message content required"));
        }

        String userId = getUserId(authHeader);
        User u = userStore.findById(userId);
        String username = u != null ? u.getUsername() : "User";

        // Topic 3: Clock Synchronization
        long lamportTs = lamportClock.send();
        long[] vClock = vectorClock.send();

        Message msg = new Message(
                UUID.randomUUID().toString(),
                channelId,
                userId,
                username,
                content,
                lamportTs,
                vClock,
                serverConfig.getServerId()
        );
        msg.setCreatedAt(Instant.now().toString());

        // Topic 2: Enqueue to MessageProcessorThread
        messageProcessor.enqueue(msg);

        // Topic 5: Replicate according to consistency mode
        consistencyManager.replicate(msg);

        // Broadcast to WebSocket clients
        wsHandler.broadcastMessage(msg);

        Map<String, Object> data = new HashMap<>();
        data.put("id", msg.getId());
        data.put("channelId", msg.getChannelId());
        data.put("content", msg.getContent());
        data.put("lamportTimestamp", msg.getLamportTimestamp());
        data.put("vectorClock", msg.getVectorClock());
        data.put("serverOrigin", msg.getServerOrigin());
        data.put("createdAt", msg.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }
}
