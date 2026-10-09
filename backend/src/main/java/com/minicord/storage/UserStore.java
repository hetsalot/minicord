package com.minicord.storage;

import com.minicord.model.User;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores users in memory with optional JDBC persistence.
 */
@Component
public class UserStore {
    private static final Logger log = LoggerFactory.getLogger(UserStore.class);

    private final ConcurrentHashMap<String, User> usersById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, User> usersByEmail = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        // Seed default demo users for convenience
        createDemoUserIfNotExists("alice", "alice@example.com", "password123");
        createDemoUserIfNotExists("bob", "bob@example.com", "password123");
        createDemoUserIfNotExists("charlie", "charlie@example.com", "password123");
    }

    private void createDemoUserIfNotExists(String username, String email, String password) {
        if (!usersByEmail.containsKey(email.toLowerCase())) {
            String hash = BCrypt.hashpw(password, BCrypt.gensalt());
            User u = new User(UUID.randomUUID().toString(), username, email.toLowerCase(), hash);
            usersById.put(u.getId(), u);
            usersByEmail.put(u.getEmail(), u);
        }
    }

    public User registerUser(String username, String email, String rawPassword) {
        String cleanEmail = email.toLowerCase().trim();
        if (usersByEmail.containsKey(cleanEmail)) {
            return null; // Already exists
        }

        String hashedPassword = BCrypt.hashpw(rawPassword, BCrypt.gensalt());
        String id = UUID.randomUUID().toString();
        User user = new User(id, username.trim(), cleanEmail, hashedPassword);

        usersById.put(user.getId(), user);
        usersByEmail.put(user.getEmail(), user);

        if (jdbcTemplate != null) {
            try {
                String sql = "INSERT INTO users (id, username, email, password, status) VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT (email) DO NOTHING";
                jdbcTemplate.update(sql, user.getId(), user.getUsername(), user.getEmail(), user.getPassword(), "offline");
            } catch (Exception e) {
                log.debug("DB write for user skipped: {}", e.getMessage());
            }
        }

        return user;
    }

    public User findByEmail(String email) {
        if (email == null) return null;
        return usersByEmail.get(email.toLowerCase().trim());
    }

    public User findById(String id) {
        if (id == null) return null;
        return usersById.get(id);
    }

    public boolean verifyPassword(String rawPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(rawPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }
}
