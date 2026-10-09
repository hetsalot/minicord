package com.minicord.websocket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minicord.clock.LamportClock;
import com.minicord.clock.VectorClock;
import com.minicord.config.ServerConfig;
import com.minicord.consistency.ConsistencyManager;
import com.minicord.model.Message;
import com.minicord.rmi.impl.PresenceServiceImpl;
import com.minicord.security.JwtUtil;
import com.minicord.storage.UserStore;
import com.minicord.threading.MessageProcessorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time WebSocket handler for chat messages and DC admin dashboard events.
 * DC Topic 2: Multithreading (ConcurrentHashMap for active sessions and channel rooms)
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final Gson gson = new Gson();

    private final ServerConfig serverConfig;
    private final LamportClock lamportClock;
    private final VectorClock vectorClock;
    private final JwtUtil jwtUtil;
    private final UserStore userStore;
    private final MessageProcessorThread messageProcessor;
    private final ConsistencyManager consistencyManager;
    private final PresenceServiceImpl presenceService;

    // session.getId() -> WebSocketSession
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // session.getId() -> userId
    private final ConcurrentHashMap<String, String> sessionUsers = new ConcurrentHashMap<>();
    // session.getId() -> channelId
    private final ConcurrentHashMap<String, String> sessionChannels = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(ServerConfig serverConfig,
                                  LamportClock lamportClock,
                                  VectorClock vectorClock,
                                  JwtUtil jwtUtil,
                                  UserStore userStore,
                                  MessageProcessorThread messageProcessor,
                                  ConsistencyManager consistencyManager,
                                  PresenceServiceImpl presenceService) {
        this.serverConfig = serverConfig;
        this.lamportClock = lamportClock;
        this.vectorClock = vectorClock;
        this.jwtUtil = jwtUtil;
        this.userStore = userStore;
        this.messageProcessor = messageProcessor;
        this.consistencyManager = consistencyManager;
        this.presenceService = presenceService;
    }

    public int getActiveConnectionsCount() {
        return sessions.size();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);

        // Extract token from query params: ws://localhost:8080/ws?token=...
        URI uri = session.getUri();
        String query = uri != null ? uri.getQuery() : null;
        String userId = "anonymous";
        String username = "Anonymous";

        if (query != null && query.contains("token=")) {
            String token = query.substring(query.indexOf("token=") + 6);
            if (token.contains("&")) {
                token = token.substring(0, token.indexOf("&"));
            }
            String extractedId = jwtUtil.getUserIdFromToken(token);
            if (extractedId != null) {
                userId = extractedId;
                username = jwtUtil.getUsernameFromToken(token);
            }
        }

        sessionUsers.put(session.getId(), userId);
        presenceService.updatePresence(userId, "online");
        broadcastPresence(userId, username, "online");

        log.info("WebSocket connected: session={}, user={}", session.getId(), username);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) throws Exception {
        String payload = textMessage.getPayload();
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();
            String type = json.has("type") ? json.get("type").getAsString() : "";
            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : new JsonObject();

            String userId = sessionUsers.getOrDefault(session.getId(), "anonymous");

            switch (type) {
                case "join_channel":
                    String channelId = data.get("channelId").getAsString();
                    sessionChannels.put(session.getId(), channelId);
                    log.debug("Session {} joined channel {}", session.getId(), channelId);
                    break;

                case "leave_channel":
                    sessionChannels.remove(session.getId());
                    break;

                case "send_message":
                    handleSendMessage(data, userId);
                    break;

                case "typing":
                    handleTyping(data, userId);
                    break;

                default:
                    log.debug("Unhandled WS event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error processing incoming WebSocket payload: {}", payload, e);
        }
    }

    private void handleSendMessage(JsonObject data, String userId) {
        String channelId = data.get("channelId").getAsString();
        String content = data.get("content").getAsString();

        var user = userStore.findById(userId);
        String username = user != null ? user.getUsername() : "User";

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

        // Broadcast to local connected clients
        broadcastMessage(msg);
    }

    private void handleTyping(JsonObject data, String userId) {
        String channelId = data.get("channelId").getAsString();
        var user = userStore.findById(userId);
        String username = user != null ? user.getUsername() : "User";

        Map<String, Object> map = new HashMap<>();
        map.put("type", "user_typing");
        Map<String, Object> inner = new HashMap<>();
        inner.put("userId", userId);
        inner.put("username", username);
        inner.put("channelId", channelId);
        map.put("data", inner);

        broadcastToChannel(channelId, gson.toJson(map));
    }

    public void broadcastMessage(Message msg) {
        Map<String, Object> outer = new HashMap<>();
        outer.put("type", "new_message");

        Map<String, Object> data = new HashMap<>();
        data.put("id", msg.getId());
        data.put("channelId", msg.getChannelId());

        Map<String, Object> author = new HashMap<>();
        author.put("id", msg.getAuthorId());
        author.put("username", msg.getAuthorUsername());
        author.put("avatarUrl", null);
        data.put("author", author);

        data.put("content", msg.getContent());
        data.put("lamportTimestamp", msg.getLamportTimestamp());
        data.put("vectorClock", msg.getVectorClock());
        data.put("serverOrigin", msg.getServerOrigin());
        data.put("createdAt", msg.getCreatedAt());

        outer.put("data", data);
        broadcastToChannel(msg.getChannelId(), gson.toJson(outer));
    }

    public void broadcastToChannel(String channelId, String text) {
        TextMessage tm = new TextMessage(text);
        sessions.forEach((sId, session) -> {
            String ch = sessionChannels.get(sId);
            if (channelId.equals(ch) && session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(tm);
                    }
                } catch (IOException e) {
                    log.debug("Failed sending WS msg to {}: {}", sId, e.getMessage());
                }
            }
        });
    }

    public void broadcastToAll(String text) {
        TextMessage tm = new TextMessage(text);
        sessions.forEach((sId, session) -> {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(tm);
                    }
                } catch (IOException e) {
                    log.debug("Failed broadcasting WS msg to {}: {}", sId, e.getMessage());
                }
            }
        });
    }

    public void broadcastPresence(String userId, String username, String status) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "presence_update");
        Map<String, Object> d = new HashMap<>();
        d.put("userId", userId);
        d.put("username", username);
        d.put("status", status);
        map.put("data", d);
        broadcastToAll(gson.toJson(map));
    }

    public void broadcastDashboardEvent(String type, Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("data", data);
        broadcastToAll(gson.toJson(map));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        String userId = sessionUsers.remove(session.getId());
        sessionChannels.remove(session.getId());

        if (userId != null && !userId.equals("anonymous")) {
            presenceService.updatePresence(userId, "offline");
            var user = userStore.findById(userId);
            String username = user != null ? user.getUsername() : "User";
            broadcastPresence(userId, username, "offline");
        }
        log.info("WebSocket disconnected: session={}", session.getId());
    }
}
