# MiniCord — API & RMI Interface Specification

> Complete specification of all REST endpoints, WebSocket events, and RMI interfaces.

---

## 1. REST API

### Base URL
```
http://localhost:8080/api   (via Load Balancer)
```

All responses follow this format:
```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

Error response:
```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "AUTH_FAILED",
    "message": "Invalid credentials"
  }
}
```

---

### 1.1 Authentication

#### POST `/api/auth/register`
Register a new user.

**Request:**
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "securepassword123"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "uuid-1234",
    "username": "alice",
    "email": "alice@example.com",
    "token": "eyJhbGciOiJI..."
  }
}
```

#### POST `/api/auth/login`
Login with credentials.

**Request:**
```json
{
  "email": "alice@example.com",
  "password": "securepassword123"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": "uuid-1234",
    "username": "alice",
    "token": "eyJhbGciOiJI..."
  }
}
```

#### POST `/api/auth/logout`
Logout (invalidate token).

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
  "success": true,
  "data": { "message": "Logged out successfully" }
}
```

---

### 1.2 Servers (Guilds)

#### GET `/api/servers`
List all servers the authenticated user belongs to.

**Headers:** `Authorization: Bearer <token>`

**Response (200):**
```json
{
  "success": true,
  "data": {
    "servers": [
      {
        "id": "server-uuid-1",
        "name": "DC Demo Server",
        "ownerId": "uuid-1234",
        "inviteCode": "abc123",
        "memberCount": 5,
        "createdAt": "2026-10-07T10:00:00Z"
      }
    ]
  }
}
```

#### POST `/api/servers`
Create a new server.

**Request:**
```json
{
  "name": "My Awesome Server"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "server-uuid-2",
    "name": "My Awesome Server",
    "ownerId": "uuid-1234",
    "inviteCode": "xyz789",
    "channels": [
      { "id": "channel-uuid-1", "name": "general" }
    ]
  }
}
```

#### POST `/api/servers/join`
Join a server via invite code.

**Request:**
```json
{
  "inviteCode": "abc123"
}
```

**Response (200):**
```json
{
  "success": true,
  "data": {
    "id": "server-uuid-1",
    "name": "DC Demo Server",
    "channels": [...]
  }
}
```

#### DELETE `/api/servers/:serverId`
Delete a server (owner only).

**Response (200):**
```json
{
  "success": true,
  "data": { "message": "Server deleted" }
}
```

---

### 1.3 Channels

#### GET `/api/servers/:serverId/channels`
List channels in a server.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "channels": [
      {
        "id": "channel-uuid-1",
        "serverId": "server-uuid-1",
        "name": "general",
        "createdAt": "2026-10-07T10:00:00Z"
      },
      {
        "id": "channel-uuid-2",
        "serverId": "server-uuid-1",
        "name": "random",
        "createdAt": "2026-10-07T10:05:00Z"
      }
    ]
  }
}
```

#### POST `/api/servers/:serverId/channels`
Create a channel in a server.

**Request:**
```json
{
  "name": "dev-chat"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "channel-uuid-3",
    "serverId": "server-uuid-1",
    "name": "dev-chat"
  }
}
```

#### DELETE `/api/channels/:channelId`
Delete a channel (server owner only).

---

### 1.4 Messages

#### GET `/api/channels/:channelId/messages`
Get message history for a channel.

**Query Params:**
- `limit` (default: 50) — number of messages
- `before` (optional) — message ID for pagination (cursor-based)

**Response (200):**
```json
{
  "success": true,
  "data": {
    "messages": [
      {
        "id": "msg-uuid-1",
        "channelId": "channel-uuid-1",
        "author": {
          "id": "uuid-1234",
          "username": "alice",
          "avatarUrl": null
        },
        "content": "Hello everyone!",
        "lamportTimestamp": 42,
        "vectorClock": [42, 38, 40],
        "serverOrigin": 1,
        "createdAt": "2026-10-07T10:30:05Z"
      }
    ],
    "hasMore": true
  }
}
```

#### POST `/api/channels/:channelId/messages`
Send a message to a channel.

**Request:**
```json
{
  "content": "Hello everyone!"
}
```

**Response (201):**
```json
{
  "success": true,
  "data": {
    "id": "msg-uuid-2",
    "channelId": "channel-uuid-1",
    "content": "Hello everyone!",
    "lamportTimestamp": 43,
    "vectorClock": [43, 38, 40],
    "serverOrigin": 1,
    "createdAt": "2026-10-07T10:30:07Z"
  }
}
```

---

### 1.5 Admin / DC Dashboard Endpoints

#### GET `/api/admin/nodes`
Get status of all backend nodes.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "nodes": [
      {
        "id": 1,
        "name": "minicord-server-1",
        "status": "ALIVE",
        "role": "FOLLOWER",
        "connections": 12,
        "lastHeartbeat": "2026-10-07T10:30:05Z",
        "uptime": "2h 15m"
      },
      {
        "id": 2,
        "name": "minicord-server-2",
        "status": "ALIVE",
        "role": "COORDINATOR",
        "connections": 8,
        "lastHeartbeat": "2026-10-07T10:30:05Z",
        "uptime": "2h 15m"
      },
      {
        "id": 3,
        "name": "minicord-server-3",
        "status": "DEAD",
        "role": "NONE",
        "connections": 0,
        "lastHeartbeat": "2026-10-07T10:28:53Z",
        "uptime": "0"
      }
    ],
    "coordinator": 2
  }
}
```

#### GET `/api/admin/clocks`
Get clock state for all nodes.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "clocks": [
      {
        "serverId": 1,
        "lamportClock": 42,
        "vectorClock": [42, 38, 40],
        "physicalTime": "2026-10-07T10:30:05.123Z",
        "clockOffset": 0
      },
      {
        "serverId": 2,
        "lamportClock": 38,
        "vectorClock": [40, 38, 39],
        "physicalTime": "2026-10-07T10:30:05.456Z",
        "clockOffset": 33
      }
    ],
    "lastSyncTime": "2026-10-07T10:29:00Z",
    "syncAlgorithm": "BERKELEY"
  }
}
```

#### POST `/api/admin/clock/skew`
Introduce clock skew on a specific server.

**Request:**
```json
{
  "serverId": 2,
  "offsetMs": 5000
}
```

#### POST `/api/admin/clock/sync`
Trigger Berkeley clock synchronization.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "adjustments": [
      { "serverId": 1, "offsetMs": 33 },
      { "serverId": 2, "offsetMs": -367 },
      { "serverId": 3, "offsetMs": 333 }
    ],
    "averageTime": "2026-10-07T10:30:05.133Z"
  }
}
```

#### GET `/api/admin/election/history`
Get election history.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "elections": [
      {
        "id": 1,
        "algorithm": "BULLY",
        "oldCoordinator": 3,
        "newCoordinator": 2,
        "triggeredBy": 1,
        "timestamp": "2026-10-07T10:30:05Z",
        "steps": [
          { "time": "10:30:05.100", "action": "S1 sends ELECTION to S2" },
          { "time": "10:30:05.150", "action": "S2 sends OK to S1" },
          { "time": "10:30:05.200", "action": "S1 sends ELECTION to S3" },
          { "time": "10:30:06.200", "action": "S3 timeout (1s)" },
          { "time": "10:30:06.250", "action": "S2 sends ELECTION to S3" },
          { "time": "10:30:07.250", "action": "S3 timeout (1s)" },
          { "time": "10:30:07.300", "action": "S2 declares COORDINATOR" },
          { "time": "10:30:07.350", "action": "S2 sends COORDINATOR to S1" }
        ],
        "durationMs": 2250
      }
    ]
  }
}
```

#### POST `/api/admin/election/trigger`
Manually trigger a leader election.

**Request:**
```json
{
  "algorithm": "RING"
}
```

#### POST `/api/admin/node/:nodeId/kill`
Simulate killing a backend node.

**Response (200):**
```json
{
  "success": true,
  "data": { "message": "Server 2 killed" }
}
```

#### POST `/api/admin/node/:nodeId/revive`
Revive a killed backend node.

#### POST `/api/admin/consistency/mode`
Switch consistency mode.

**Request:**
```json
{
  "mode": "EVENTUAL",
  "quorumW": 2,
  "quorumR": 2
}
```

#### GET `/api/admin/consistency/status`
Get consistency status.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "mode": "EVENTUAL",
    "servers": [
      { "id": 1, "messageCount": 142, "latestMessageId": "msg-142" },
      { "id": 2, "messageCount": 140, "latestMessageId": "msg-140" },
      { "id": 3, "messageCount": 142, "latestMessageId": "msg-142" }
    ],
    "replicationLag": {
      "server2": { "behind": 2, "lagMs": 1500 }
    },
    "conflictsDetected": 0
  }
}
```

#### GET `/api/admin/lb/stats`
Get load balancer statistics.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "algorithm": "ROUND_ROBIN",
    "servers": [
      {
        "id": 1,
        "status": "ACTIVE",
        "activeConnections": 45,
        "totalRequests": 1240,
        "requestsPerSecond": 12.5,
        "avgResponseTimeMs": 23,
        "healthCheckStatus": "HEALTHY",
        "lastHealthCheck": "2026-10-07T10:30:05Z"
      },
      {
        "id": 2,
        "status": "ACTIVE",
        "activeConnections": 42,
        "totalRequests": 1195,
        "requestsPerSecond": 11.8,
        "avgResponseTimeMs": 25,
        "healthCheckStatus": "HEALTHY",
        "lastHealthCheck": "2026-10-07T10:30:05Z"
      },
      {
        "id": 3,
        "status": "DOWN",
        "activeConnections": 0,
        "totalRequests": 890,
        "requestsPerSecond": 0,
        "avgResponseTimeMs": 0,
        "healthCheckStatus": "UNHEALTHY",
        "failedChecks": 3,
        "lastHealthCheck": "2026-10-07T10:29:50Z"
      }
    ],
    "totalRequestsPerSecond": 24.3,
    "totalActiveConnections": 87
  }
}
```

#### POST `/api/admin/lb/algorithm`
Switch load balancing algorithm.

**Request:**
```json
{
  "algorithm": "LEAST_CONNECTIONS"
}
```

#### GET `/api/admin/threads/:serverId`
Get thread dump for a specific server.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "serverId": 1,
    "threads": [
      {
        "name": "ws-handler-alice",
        "state": "RUNNABLE",
        "daemon": false,
        "info": "Handling WebSocket for user alice"
      },
      {
        "name": "msg-processor",
        "state": "WAITING",
        "daemon": true,
        "info": "Waiting on BlockingQueue (empty)"
      },
      {
        "name": "replication-pool-1",
        "state": "RUNNABLE",
        "daemon": true,
        "info": "Executing RMI call to Server 2"
      },
      {
        "name": "heartbeat-daemon",
        "state": "TIMED_WAITING",
        "daemon": true,
        "info": "Sleeping (next heartbeat in 2.1s)"
      }
    ],
    "threadPool": {
      "name": "replication-pool",
      "coreSize": 3,
      "maxSize": 5,
      "activeCount": 1,
      "queueSize": 0,
      "completedTasks": 47
    }
  }
}
```

#### GET `/api/admin/rmi/logs`
Get recent RMI call logs.

**Query Params:**
- `limit` (default: 50)
- `method` (optional filter: `replicateMessage`, `election`, `clockSync`, etc.)

**Response (200):**
```json
{
  "success": true,
  "data": {
    "logs": [
      {
        "timestamp": "2026-10-07T10:30:05.123Z",
        "method": "replicateMessage",
        "source": 1,
        "destination": 2,
        "status": "SUCCESS",
        "durationMs": 12,
        "payload": "msg-uuid-42"
      },
      {
        "timestamp": "2026-10-07T10:30:05.125Z",
        "method": "replicateMessage",
        "source": 1,
        "destination": 3,
        "status": "FAILED",
        "durationMs": 5003,
        "error": "java.rmi.ConnectException: Connection refused"
      }
    ]
  }
}
```

---

## 2. WebSocket Events

### Connection
```
ws://localhost:8080/ws?token=<JWT>
```

### Client → Server Events

#### `join_channel`
```json
{
  "type": "join_channel",
  "channelId": "channel-uuid-1"
}
```

#### `leave_channel`
```json
{
  "type": "leave_channel",
  "channelId": "channel-uuid-1"
}
```

#### `send_message`
```json
{
  "type": "send_message",
  "channelId": "channel-uuid-1",
  "content": "Hello everyone!"
}
```

#### `typing_start`
```json
{
  "type": "typing_start",
  "channelId": "channel-uuid-1"
}
```

#### `typing_stop`
```json
{
  "type": "typing_stop",
  "channelId": "channel-uuid-1"
}
```

### Server → Client Events

#### `new_message`
```json
{
  "type": "new_message",
  "data": {
    "id": "msg-uuid-1",
    "channelId": "channel-uuid-1",
    "author": { "id": "uuid-1234", "username": "alice" },
    "content": "Hello everyone!",
    "lamportTimestamp": 43,
    "vectorClock": [43, 38, 40],
    "serverOrigin": 1,
    "createdAt": "2026-10-07T10:30:07Z"
  }
}
```

#### `presence_update`
```json
{
  "type": "presence_update",
  "data": {
    "userId": "uuid-1234",
    "username": "alice",
    "status": "online"
  }
}
```

#### `user_typing`
```json
{
  "type": "user_typing",
  "data": {
    "userId": "uuid-1234",
    "username": "alice",
    "channelId": "channel-uuid-1"
  }
}
```

### Server → Admin Dashboard Events

#### `node_status_update`
```json
{
  "type": "node_status_update",
  "data": {
    "nodeId": 3,
    "status": "DEAD",
    "reason": "Missed 3 consecutive heartbeats",
    "timestamp": "2026-10-07T10:30:05Z"
  }
}
```

#### `election_update`
```json
{
  "type": "election_update",
  "data": {
    "phase": "IN_PROGRESS",
    "algorithm": "BULLY",
    "step": {
      "action": "S2 sends ELECTION to S3",
      "result": "TIMEOUT"
    },
    "timestamp": "2026-10-07T10:30:06Z"
  }
}
```

#### `election_complete`
```json
{
  "type": "election_complete",
  "data": {
    "algorithm": "BULLY",
    "newCoordinator": 2,
    "oldCoordinator": 3,
    "durationMs": 2250,
    "timestamp": "2026-10-07T10:30:07Z"
  }
}
```

#### `clock_update`
```json
{
  "type": "clock_update",
  "data": {
    "serverId": 1,
    "lamportClock": 44,
    "vectorClock": [44, 39, 40],
    "physicalTime": "2026-10-07T10:30:08Z"
  }
}
```

#### `consistency_update`
```json
{
  "type": "consistency_update",
  "data": {
    "mode": "EVENTUAL",
    "serverCounts": [
      { "id": 1, "count": 143 },
      { "id": 2, "count": 141 },
      { "id": 3, "count": 143 }
    ],
    "replicationLag": { "server2": 2 }
  }
}
```

#### `lb_stats_update`
```json
{
  "type": "lb_stats_update",
  "data": {
    "algorithm": "ROUND_ROBIN",
    "servers": [
      { "id": 1, "connections": 45, "rps": 12.5 },
      { "id": 2, "connections": 42, "rps": 11.8 },
      { "id": 3, "connections": 0, "rps": 0, "status": "DOWN" }
    ]
  }
}
```

#### `rmi_log_entry`
```json
{
  "type": "rmi_log_entry",
  "data": {
    "timestamp": "2026-10-07T10:30:05.123Z",
    "method": "replicateMessage",
    "source": 1,
    "destination": 2,
    "status": "SUCCESS",
    "durationMs": 12
  }
}
```

---

## 3. RMI Interface Definitions

### 3.1 MessageReplicationService

```java
package com.minicord.rmi.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface MessageReplicationService extends Remote {
    
    /**
     * Replicate a message to this server.
     * Called by the server that originally received the message.
     * @param message The message DTO to replicate
     * @return true if successfully stored
     */
    boolean replicateMessage(MessageDTO message) throws RemoteException;
    
    /**
     * Get all messages since a given Lamport timestamp.
     * Used for recovery/sync after a server comes back online.
     * @param sinceTimestamp The Lamport timestamp to start from
     * @return List of messages since that timestamp
     */
    List<MessageDTO> getMessagesSince(long sinceTimestamp) throws RemoteException;
    
    /**
     * Health check — returns true if this server is operational.
     */
    boolean healthCheck() throws RemoteException;
    
    /**
     * Get the total message count on this server.
     * Used for consistency monitoring.
     */
    int getMessageCount() throws RemoteException;
}
```

### 3.2 PresenceService

```java
package com.minicord.rmi.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface PresenceService extends Remote {
    
    /**
     * Update a user's presence status on this server.
     * @param userId The user ID
     * @param status "online", "offline", "idle"
     */
    void updatePresence(String userId, String status) throws RemoteException;
    
    /**
     * Get all user presence states from this server.
     * @return Map of userId → status
     */
    Map<String, String> getAllPresence() throws RemoteException;
}
```

### 3.3 ElectionService

```java
package com.minicord.rmi.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ElectionService extends Remote {
    
    /**
     * Bully Algorithm: Receive an ELECTION message.
     * If this server's ID is higher, it responds OK and starts its own election.
     * @param candidateId The ID of the server calling the election
     * @return ElectionResponse with OK/REJECT
     */
    ElectionResponse election(int candidateId) throws RemoteException;
    
    /**
     * Receive a COORDINATOR announcement.
     * The sender is declaring itself as the new coordinator.
     * @param coordinatorId The ID of the new coordinator
     */
    void coordinator(int coordinatorId) throws RemoteException;
    
    /**
     * Ring Algorithm: Forward an election message around the ring.
     * @param participantIds List of server IDs that have been added to the election message
     * @param initiatorId The ID of the server that started the election
     * @return Updated list of participant IDs (with this server's ID added)
     */
    List<Integer> ringElection(List<Integer> participantIds, int initiatorId) throws RemoteException;
    
    /**
     * Check if this server is alive and responsive.
     */
    boolean isAlive() throws RemoteException;
    
    /**
     * Get this server's ID.
     */
    int getServerId() throws RemoteException;
}
```

### 3.4 ClockSyncService

```java
package com.minicord.rmi.interfaces;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClockSyncService extends Remote {
    
    /**
     * Get this server's current physical time (in milliseconds since epoch).
     * Used by the Berkeley algorithm coordinator.
     */
    long getLocalTime() throws RemoteException;
    
    /**
     * Adjust this server's clock by the given offset.
     * Called by the Berkeley algorithm coordinator.
     * @param offsetMs Milliseconds to add (positive) or subtract (negative)
     */
    void adjustClock(long offsetMs) throws RemoteException;
    
    /**
     * Get this server's current Lamport clock value.
     */
    long getLamportClock() throws RemoteException;
    
    /**
     * Get this server's current vector clock.
     * @return Array of clock values, indexed by server ID
     */
    long[] getVectorClock() throws RemoteException;
    
    /**
     * Force set the clock offset (for demo simulation).
     * @param offsetMs The offset to set (simulates clock skew)
     */
    void setClockSkew(long offsetMs) throws RemoteException;
}
```

### 3.5 Supporting DTOs

```java
// MessageDTO — Serializable message for RMI transport
public class MessageDTO implements Serializable {
    private String id;
    private String channelId;
    private String authorId;
    private String authorUsername;
    private String content;
    private long lamportTimestamp;
    private long[] vectorClock;
    private int serverOrigin;
    private long createdAt;  // epoch millis
}

// ElectionResponse — Response to an election request
public class ElectionResponse implements Serializable {
    private boolean ok;      // true = "I have higher ID, I'll take over"
    private int responderId;
}
```

---

## 4. Health Check Endpoint

Every backend server exposes:

#### GET `/health`

**Response (200 — Healthy):**
```json
{
  "status": "UP",
  "serverId": 1,
  "role": "FOLLOWER",
  "uptime": "2h 15m",
  "connections": 12,
  "lamportClock": 42,
  "dbConnected": true
}
```

**Response (503 — Unhealthy):**
```json
{
  "status": "DOWN",
  "serverId": 1,
  "error": "Database connection failed"
}
```
