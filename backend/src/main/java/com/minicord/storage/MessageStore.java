package com.minicord.storage;

import com.google.gson.Gson;
import com.minicord.model.Message;
import com.minicord.rmi.dto.MessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Message storage combining an in-memory thread-safe cache with optional JDBC persistence.
 * DC Topic 2: Multithreading (ReentrantReadWriteLock for concurrent reads and exclusive writes)
 * DC Topic 3: Message ordering by Lamport timestamp + server origin
 */
@Component
public class MessageStore {
    private static final Logger log = LoggerFactory.getLogger(MessageStore.class);
    private static final Gson gson = new Gson();

    private final List<Message> messageCache = new ArrayList<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    /**
     * Store message thread-safely using write lock and optionally persist to DB.
     */
    public Message saveMessage(Message msg) {
        if (msg.getId() == null || msg.getId().isBlank()) {
            msg.setId(UUID.randomUUID().toString());
        }
        if (msg.getCreatedAt() == null || msg.getCreatedAt().isBlank()) {
            msg.setCreatedAt(Instant.now().toString());
        }

        writeLock.lock();
        try {
            // Check for duplicate
            boolean exists = messageCache.stream().anyMatch(m -> m.getId().equals(msg.getId()));
            if (!exists) {
                messageCache.add(msg);
                // Sort by Lamport timestamp, tie-breaking by serverOrigin
                messageCache.sort(Comparator
                        .comparingLong(Message::getLamportTimestamp)
                        .thenComparingInt(Message::getServerOrigin));
            }
        } finally {
            writeLock.unlock();
        }

        // Async or best-effort DB write
        if (jdbcTemplate != null) {
            try {
                String sql = "INSERT INTO messages (id, channel_id, author_id, content, lamport_ts, vector_clock, server_origin, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
                String vcJson = gson.toJson(msg.getVectorClock() != null ? msg.getVectorClock() : new long[0]);
                jdbcTemplate.update(sql,
                        msg.getId(),
                        msg.getChannelId(),
                        msg.getAuthorId(),
                        msg.getContent(),
                        msg.getLamportTimestamp(),
                        vcJson,
                        msg.getServerOrigin(),
                        Timestamp.from(Instant.now()));
            } catch (Exception e) {
                log.debug("Database write skipped/failed: {}", e.getMessage());
            }
        }

        return msg;
    }

    /**
     * Get messages for a channel ordered by Lamport timestamp.
     */
    public List<Message> getMessagesByChannel(String channelId, int limit, String beforeId) {
        readLock.lock();
        try {
            List<Message> channelMsgs = messageCache.stream()
                    .filter(m -> channelId.equals(m.getChannelId()))
                    .collect(Collectors.toList());

            if (beforeId != null && !beforeId.isBlank()) {
                int index = -1;
                for (int i = 0; i < channelMsgs.size(); i++) {
                    if (channelMsgs.get(i).getId().equals(beforeId)) {
                        index = i;
                        break;
                    }
                }
                if (index > 0) {
                    channelMsgs = channelMsgs.subList(0, index);
                }
            }

            if (channelMsgs.size() > limit) {
                return channelMsgs.subList(channelMsgs.size() - limit, channelMsgs.size());
            }
            return new ArrayList<>(channelMsgs);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Get messages with Lamport timestamp > sinceTimestamp (for recovery sync).
     */
    public List<MessageDTO> getMessagesSince(long sinceTimestamp) {
        readLock.lock();
        try {
            return messageCache.stream()
                    .filter(m -> m.getLamportTimestamp() > sinceTimestamp)
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        } finally {
            readLock.unlock();
        }
    }

    public int getMessageCount() {
        readLock.lock();
        try {
            return messageCache.size();
        } finally {
            readLock.unlock();
        }
    }

    public Message getLatestMessage() {
        readLock.lock();
        try {
            if (messageCache.isEmpty()) return null;
            return messageCache.get(messageCache.size() - 1);
        } finally {
            readLock.unlock();
        }
    }

    public MessageDTO toDTO(Message m) {
        return new MessageDTO(
                m.getId(),
                m.getChannelId(),
                m.getAuthorId(),
                m.getAuthorUsername(),
                m.getContent(),
                m.getLamportTimestamp(),
                m.getVectorClock(),
                m.getServerOrigin(),
                Instant.now().toEpochMilli()
        );
    }

    public Message fromDTO(MessageDTO dto) {
        Message m = new Message();
        m.setId(dto.getId());
        m.setChannelId(dto.getChannelId());
        m.setAuthorId(dto.getAuthorId());
        m.setAuthorUsername(dto.getAuthorUsername());
        m.setContent(dto.getContent());
        m.setLamportTimestamp(dto.getLamportTimestamp());
        m.setVectorClock(dto.getVectorClock());
        m.setServerOrigin(dto.getServerOrigin());
        m.setCreatedAt(Instant.ofEpochMilli(dto.getCreatedAt()).toString());
        return m;
    }
}
