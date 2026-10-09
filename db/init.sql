-- MiniCord Database Schema
-- PostgreSQL 15

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id          VARCHAR(36) PRIMARY KEY,
    username    VARCHAR(50) UNIQUE NOT NULL,
    email       VARCHAR(100) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    avatar_url  VARCHAR(255) DEFAULT NULL,
    status      VARCHAR(20) DEFAULT 'offline',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Servers (guilds)
CREATE TABLE IF NOT EXISTS servers (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    owner_id    VARCHAR(36) REFERENCES users(id) ON DELETE CASCADE,
    invite_code VARCHAR(10) UNIQUE NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Channels within a server
CREATE TABLE IF NOT EXISTS channels (
    id          VARCHAR(36) PRIMARY KEY,
    server_id   VARCHAR(36) REFERENCES servers(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Server membership (many-to-many)
CREATE TABLE IF NOT EXISTS server_members (
    user_id     VARCHAR(36) REFERENCES users(id) ON DELETE CASCADE,
    server_id   VARCHAR(36) REFERENCES servers(id) ON DELETE CASCADE,
    joined_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, server_id)
);

-- Messages
CREATE TABLE IF NOT EXISTS messages (
    id              VARCHAR(36) PRIMARY KEY,
    channel_id      VARCHAR(36) REFERENCES channels(id) ON DELETE CASCADE,
    author_id       VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    content         TEXT NOT NULL,
    lamport_ts      BIGINT NOT NULL DEFAULT 0,
    vector_clock    TEXT DEFAULT '[]',
    server_origin   INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Election history log
CREATE TABLE IF NOT EXISTS election_log (
    id              SERIAL PRIMARY KEY,
    algorithm       VARCHAR(10) NOT NULL,
    old_coordinator INT,
    new_coordinator INT,
    triggered_by    INT,
    duration_ms     INT,
    details         TEXT DEFAULT '[]',
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages(channel_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_lamport ON messages(channel_id, lamport_ts);
CREATE INDEX IF NOT EXISTS idx_server_members_user ON server_members(user_id);
CREATE INDEX IF NOT EXISTS idx_server_members_server ON server_members(server_id);
CREATE INDEX IF NOT EXISTS idx_channels_server ON channels(server_id);
