# MiniCord — System Architecture

> Detailed architecture document covering Docker topology, networking, communication protocols, and data flow.

---

## 1. Architecture Overview

MiniCord follows a **distributed microservice architecture** where every component runs in its own Docker container. The system is designed so that the architecture itself IS the demonstration of distributed computing.

```
                         ┌─────────────────┐
                         │   User Browser   │
                         │   (React App)    │
                         └────────┬─────────┘
                                  │ HTTP / WebSocket
                                  ▼
                    ┌─────────────────────────────┐
                    │    minicord-frontend         │
                    │    (Nginx + Static Files)    │
                    │    Port 3000                 │
                    └─────────────┬───────────────┘
                                  │ Reverse Proxy
                                  ▼
                    ┌─────────────────────────────┐
                    │    minicord-lb               │
                    │    (Load Balancer)           │
                    │    Port 8080                 │
                    │                             │
                    │  Algorithms:                │
                    │  • Round Robin              │
                    │  • Least Connections        │
                    │  • Weighted Round Robin     │
                    └──┬──────────┬───────────┬───┘
                       │          │           │
              ┌────────┘    ┌─────┘     ┌─────┘
              ▼             ▼           ▼
     ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
     │minicord-     │ │minicord-     │ │minicord-     │
     │server-1      │ │server-2      │ │server-3      │
     │(Java)        │ │(Java)        │ │(Java)        │
     │              │ │              │ │              │
     │ID=1          │ │ID=2          │ │ID=3          │
     │Port 9001     │ │Port 9002     │ │Port 9003     │
     │              │ │              │ │              │
     │• REST API    │ │• REST API    │ │• REST API    │
     │• WebSocket   │ │• WebSocket   │ │• WebSocket   │
     │• RMI Client  │ │• RMI Client  │ │• RMI Client  │
     │• RMI Server  │ │• RMI Server  │ │• RMI Server  │
     │• Lamport Clk │ │• Lamport Clk │ │• Lamport Clk │
     │• Vector Clk  │ │• Vector Clk  │ │• Vector Clk  │
     │• Election    │ │• Election    │ │• Election    │
     │• Heartbeat   │ │• Heartbeat   │ │• Heartbeat   │
     └──┬───────┬───┘ └──┬───────┬───┘ └──┬───────┬───┘
        │       │         │       │         │       │
        │       │    RMI  │  RMI  │    RMI  │       │
        │       └────────►│◄──────┘◄────────┘       │
        │                 │                         │
        │            ┌────┴─────┐                   │
        │            │minicord- │                   │
        │            │rmi-      │                   │
        │            │registry  │                   │
        │            │Port 1099 │                   │
        │            └──────────┘                   │
        │                                           │
        │         ┌──────────────────┐              │
        └────────►│ minicord-db-     │◄─────────────┘
                  │ primary          │
                  │ (PostgreSQL)     │
                  │ Port 5432        │
                  │                  │
                  │ Streaming        │
                  │ Replication      │
                  │      │           │
                  └──────┼───────────┘
                         │
                         ▼
                  ┌──────────────────┐
                  │ minicord-db-     │
                  │ replica          │
                  │ (PostgreSQL)     │
                  │ Port 5433        │
                  └──────────────────┘
```

---

## 2. Docker Network Topology

All containers are on a single Docker bridge network: `minicord-net`.

```
Docker Bridge Network: minicord-net (172.20.0.0/16)
├── minicord-frontend     172.20.0.10    Port 3000 → Host
├── minicord-lb           172.20.0.11    Port 8080 → Host
├── minicord-server-1     172.20.0.21    Port 9001 (internal)
├── minicord-server-2     172.20.0.22    Port 9002 (internal)
├── minicord-server-3     172.20.0.23    Port 9003 (internal)
├── minicord-rmi-registry 172.20.0.30    Port 1099 (internal)
├── minicord-db-primary   172.20.0.40    Port 5432 (internal)
└── minicord-db-replica   172.20.0.41    Port 5433 (internal)
```

**Service Discovery:** Containers reference each other by **Docker service name** (DNS resolution within the bridge network). For example, `server-1` connects to the database via hostname `db-primary`.

---

## 3. Communication Protocols

### 3.1 Client ↔ Backend (External)

| Protocol | Use Case | Format |
|---|---|---|
| **HTTP/REST** | CRUD operations (auth, servers, channels, messages, admin) | JSON |
| **WebSocket** | Real-time messaging, presence, admin dashboard updates | JSON frames |

### 3.2 Backend ↔ Backend (Internal — via RMI)

| RMI Interface | Methods | Purpose |
|---|---|---|
| `MessageReplicationService` | `replicateMessage()`, `getMessagesSince()`, `healthCheck()` | Message replication |
| `PresenceService` | `updatePresence()`, `getAllPresence()` | User presence sync |
| `ElectionService` | `election()`, `coordinator()`, `isAlive()` | Leader election |
| `ClockSyncService` | `getLocalTime()`, `adjustClock()`, `getLamportClock()` | Clock synchronization |

### 3.3 Backend ↔ Database

| Protocol | Use Case |
|---|---|
| **JDBC** (PostgreSQL driver) | All persistent data operations |

### 3.4 Load Balancer ↔ Backend

| Protocol | Use Case |
|---|---|
| **HTTP** | Proxied REST requests + health checks (`GET /health`) |
| **WebSocket** | Proxied WebSocket connections (sticky sessions) |

---

## 4. Data Flow Diagrams

### 4.1 User Sends a Message

```
User types "Hello" in #general
         │
         ▼
[React Frontend] ──WebSocket──► [Load Balancer]
                                      │
                                      │ Route to Server 1
                                      │ (based on sticky session)
                                      ▼
                              [Server 1 (Java)]
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
           [1. Increment       [2. Store in      [3. Replicate via
            Lamport Clock]      PostgreSQL]       RMI thread pool]
                                                       │
                                        ┌──────────────┤
                                        ▼              ▼
                                   [Server 2]     [Server 3]
                                   (RMI call)     (RMI call)
                                   replicateMsg   replicateMsg
                                        │              │
                                        ▼              ▼
                                   [Store in      [Store in
                                    local DB]      local DB]
                                        │              │
                    ┌───────────────────┤              │
                    ▼                   ▼              ▼
           [Broadcast to all connected WebSocket clients
            on all servers in that channel]
                    │
                    ▼
           [All users see "Hello" with Lamport timestamp]
```

### 4.2 Server Failure & Recovery

```
[Server 3 crashes / docker stop]
         │
         ▼
[Coordinator (Server with highest alive ID)]
    │
    ├── Misses heartbeat from S3 (3s)
    ├── Misses heartbeat from S3 (6s)
    ├── Misses heartbeat from S3 (9s) ←── THRESHOLD
    │
    ▼
[Declare S3 DEAD]
    │
    ├── Notify Load Balancer → remove S3 from pool
    ├── Notify all servers → update cluster view
    ├── Notify Dashboard → S3 goes 🔴
    │
    ▼
[If S3 was Coordinator]
    │
    ▼
[Trigger Election (Bully or Ring)]
    │
    ▼
[New Coordinator elected]
    │
    ▼
[New Coordinator starts Berkeley sync + heartbeat monitoring]

═══════════ RECOVERY ═══════════

[Server 3 restarts / docker start]
    │
    ▼
[S3 registers RMI services]
    │
    ▼
[S3 contacts other servers]
    │
    ├── getMessagesSince(lastKnownTimestamp) → sync missed messages
    ├── getAllPresence() → sync user presence
    │
    ▼
[S3 sends heartbeat to Coordinator]
    │
    ▼
[Coordinator marks S3 as ALIVE]
    │
    ├── Notify Load Balancer → add S3 back to pool
    ├── Notify Dashboard → S3 goes 🟢
    │
    ▼
[If S3 has higher ID than current Coordinator]
    │
    ▼
[Trigger new election → S3 becomes Coordinator]
```

### 4.3 Bully Election Flow

```
Initial State: S3 is Coordinator, S1 (ID=1), S2 (ID=2), S3 (ID=3)
S3 crashes.

S1 detects S3 failure:
    │
    ├── S1 sends ELECTION to S2 (higher ID)  ──────►  S2 receives
    ├── S1 sends ELECTION to S3 (higher ID)  ──────►  S3 is DEAD (timeout)
    │                                                    │
    │                                                    ▼
    │                                           S2 sends OK to S1
    │                                           S2 sends ELECTION to S3 ──► DEAD (timeout)
    │                                                    │
    │                                                    ▼
    │◄───── S1 receives OK from S2                 No response to S2
    │       S1 backs off (done)                    after timeout
    │                                                    │
    │                                                    ▼
    │                                           S2 declares self COORDINATOR
    │                                           S2 sends COORDINATOR to S1
    │                                                    │
    ▼                                                    ▼
S1 accepts S2                                   S2 is new Coordinator ✅
as Coordinator
```

### 4.4 Ring Election Flow

```
Ring: S1 → S2 → S3 → S1  (S3 is dead)

S1 detects S3 failure:
    │
    ▼
S1 creates election msg: [1]
S1 sends to next alive → S2 (skip S3, dead)
    │
    ▼
S2 receives [1], adds self → [1, 2]
S2 sends to next alive → S1 (skip S3, dead)
    │
    ▼
S1 receives [1, 2] ← message has returned to initiator
S1 picks max(1, 2) = 2 → S2 is coordinator
    │
    ▼
S1 sends COORDINATOR(S2) → S2
S2 receives, confirms self as coordinator ✅
```

---

## 5. Consistency Model Data Flow

### 5.1 Strong Consistency Write

```
Client sends message to Server 1
         │
         ▼
Server 1 writes to local DB
         │
         ├── RMI replicateMessage() → Server 2  ──► ACK ✓
         ├── RMI replicateMessage() → Server 3  ──► ACK ✓
         │
         ▼ (CountDownLatch await — all ACKs received)
         │
Server 1 confirms to client: "Message sent" ✅
```

### 5.2 Eventual Consistency Write

```
Client sends message to Server 1
         │
         ▼
Server 1 writes to local DB
         │
Server 1 confirms to client: "Message sent" ✅ (IMMEDIATELY)
         │
         ▼ (Background thread pool — async)
         ├── RMI replicateMessage() → Server 2  ──► (may take 100ms-2s)
         └── RMI replicateMessage() → Server 3  ──► (may take 100ms-2s)
         
During the lag window:
  • Query Server 1: message EXISTS ✓
  • Query Server 2: message MISSING ✗ (temporarily)
  • Query Server 3: message MISSING ✗ (temporarily)

After replication completes:
  • All servers: message EXISTS ✓
```

### 5.3 Quorum Write (W=2, R=2, N=3)

```
Client sends message to Server 1
         │
         ▼
Server 1 writes to local DB  ──► ACK 1
         │
         ├── RMI replicateMessage() → Server 2  ──► ACK 2  ✓ (W=2 met!)
         ├── RMI replicateMessage() → Server 3  ──► (async, not waited for)
         │
         ▼ (CountDownLatch with count=1 — only need 1 more ACK)
         │
Server 1 confirms to client: "Message sent" ✅

On Read (R=2):
  • Query Server 1: version 5
  • Query Server 2: version 5
  → Return version 5 (latest) ✅
```

---

## 6. Berkeley Clock Sync Flow

```
Coordinator (e.g., Server 3):
    │
    ├── RMI getLocalTime() → Server 1  ──► returns T1 = 10:30:05.100
    ├── RMI getLocalTime() → Server 2  ──► returns T2 = 10:30:05.500
    ├── Coordinator's own time             T3 = 10:30:04.800
    │
    ▼
Compute average: (T1 + T2 + T3) / 3 = 10:30:05.133
    │
    ├── Offset for S1: 10:30:05.133 - 10:30:05.100 = +0.033s
    ├── Offset for S2: 10:30:05.133 - 10:30:05.500 = -0.367s
    ├── Offset for S3: 10:30:05.133 - 10:30:04.800 = +0.333s
    │
    ├── RMI adjustClock(+33ms)  → Server 1
    ├── RMI adjustClock(-367ms) → Server 2
    ├── Coordinator adjusts own clock by +333ms
    │
    ▼
All servers now approximately at 10:30:05.133 ✅
```

---

## 7. Port Mapping Summary

| Service | Internal Port | Exposed to Host? | Host Port |
|---|---|---|---|
| Frontend (Nginx) | 80 | Yes | 3000 |
| Load Balancer | 8080 | Yes | 8080 |
| Server 1 | 9001 | No (internal only) | — |
| Server 2 | 9002 | No (internal only) | — |
| Server 3 | 9003 | No (internal only) | — |
| RMI Registry | 1099 | No (internal only) | — |
| DB Primary | 5432 | Optional (for debugging) | 5432 |
| DB Replica | 5432 | Optional (for debugging) | 5433 |

---

## 8. Failure Domains

```
┌─────────────────────────────────────────────────────┐
│                FAILURE DOMAIN MAP                    │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Frontend Failure:                                  │
│  └── Only affects UI. Backend continues running.    │
│      Users just can't access the app.               │
│                                                     │
│  Load Balancer Failure:                             │
│  └── Critical! No traffic reaches backend.          │
│      Mitigation: Direct server access fallback.     │
│                                                     │
│  Single Server Failure:                             │
│  └── Clients reconnect to other servers.            │
│      No data loss (replicated messages).            │
│      May trigger election if it was coordinator.    │
│                                                     │
│  Coordinator Failure:                               │
│  └── Election triggered. No clock sync during       │
│      election period. Chat continues on remaining   │
│      servers.                                       │
│                                                     │
│  Multiple Server Failure (2 of 3):                  │
│  └── System runs on 1 server. Quorum writes fail    │
│      (W=2 impossible). Strong consistency fails.    │
│      Eventual consistency still works.              │
│                                                     │
│  All Servers Down:                                  │
│  └── System is offline. Data persisted in DB.       │
│      Full recovery on restart.                      │
│                                                     │
│  Primary DB Failure:                                │
│  └── Read replica provides read-only access.        │
│      Writes fail. System enters degraded mode.      │
│                                                     │
│  RMI Registry Failure:                              │
│  └── New RMI registrations fail. Existing cached    │
│      references still work. Recovery on restart.    │
│                                                     │
└─────────────────────────────────────────────────────┘
```
