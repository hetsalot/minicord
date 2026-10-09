# MiniCord — Mini Discord for Distributed Computing

> A fully containerized (Docker) chat application that demonstrates **7 core Distributed Computing concepts** through a real, working product.

---

## 1. Project Overview

**MiniCord** is a simplified Discord clone — a real-time, multi-channel chat application with user authentication, server/channel management, and message history. It is **NOT** a toy demo; it is a functional chat platform whose **backend architecture** is deliberately designed to showcase distributed computing principles in action.

### What MiniCord IS
- A multi-user, real-time chat application with channels (like Discord text channels)
- A system where users can create servers, join channels, and send/receive messages in real time
- A fully Dockerized microservice architecture where every component runs in its own container
- A platform to **demonstrate, simulate, and visualize** 7 DC topics with a React dashboard

### What MiniCord is NOT
- A voice/video calling platform (text only)
- A production-grade deployment (it's a lab project)
- A monolithic application — everything is distributed by design

---

## 2. Core Features (Functional Requirements)

### 2.1 User Management
| Feature | Description |
|---|---|
| **Registration** | Users can register with username, email, password |
| **Login/Logout** | JWT-based authentication |
| **Profile** | Display username, avatar (default), online/offline status |
| **Presence** | Real-time online/offline/idle status broadcast to all connected users |

### 2.2 Server & Channel Management
| Feature | Description |
|---|---|
| **Create Server** | Any user can create a "server" (a named group space) |
| **Join/Leave Server** | Users can join via invite code or leave a server |
| **Channels** | Each server has multiple text channels (e.g., #general, #random) |
| **Default Channel** | Every server gets a #general channel on creation |
| **Channel CRUD** | Server owner can create, rename, delete channels |

### 2.3 Messaging
| Feature | Description |
|---|---|
| **Send Message** | Users send text messages to a channel |
| **Real-time Delivery** | Messages appear instantly for all users in that channel (WebSocket) |
| **Message History** | Scrollable history loaded from persistent storage |
| **Timestamps** | Every message shows a Lamport/Vector clock timestamp AND wall-clock time |
| **Message Ordering** | Messages are ordered using logical clocks (causal ordering) |

### 2.4 DC Admin Dashboard (Critical for Demo)
A special **admin/visualization panel** in the React frontend that shows:
- Live topology of all backend nodes (which are alive, which are dead)
- Current coordinator/leader node
- Load balancer distribution stats
- Clock synchronization state
- Consistency status across replicas
- Buttons to **trigger simulations** (kill nodes, cause failures, trigger elections, etc.)

---

## 3. System Architecture (High Level)

```
┌──────────────────────────────────────────────────────────────┐
│                      DOCKER NETWORK                          │
│                                                              │
│  ┌─────────┐    ┌──────────────┐    ┌─────────────────────┐ │
│  │ React   │    │   NGINX      │    │   Backend Cluster   │ │
│  │ Frontend│───▶│   Load       │───▶│  ┌───┐ ┌───┐ ┌───┐ │ │
│  │ (Vite)  │    │   Balancer   │    │  │ S1│ │ S2│ │ S3│ │ │
│  └─────────┘    └──────────────┘    │  └─┬─┘ └─┬─┘ └─┬─┘ │ │
│                                      │    │     │     │   │ │
│                                      │    ▼     ▼     ▼   │ │
│                                      │  ┌─────────────┐  │ │
│                                      │  │  RMI Registry│  │ │
│                                      │  └─────────────┘  │ │
│                                      └─────────────────────┘ │
│                                                              │
│  ┌─────────────────────┐    ┌─────────────────────────────┐ │
│  │   Database Cluster  │    │   Coordination Service      │ │
│  │  ┌────┐  ┌────┐     │    │  (Election, Clocks, Fault   │ │
│  │  │ DB │  │ DB │     │    │   Tolerance Management)     │ │
│  │  │Pri │  │Rep │     │    └─────────────────────────────┘ │
│  │  └────┘  └────┘     │                                    │
│  └─────────────────────┘                                    │
└──────────────────────────────────────────────────────────────┘
```

### Container Inventory

| Container | Technology | Purpose |
|---|---|---|
| `minicord-frontend` | React (Vite) + Nginx | UI, Admin Dashboard |
| `minicord-lb` | Nginx (or custom Java) | Load Balancer (Round Robin, Least Connections) |
| `minicord-server-1` | Java (Spring Boot / plain Java) | Backend node 1 |
| `minicord-server-2` | Java (Spring Boot / plain Java) | Backend node 2 |
| `minicord-server-3` | Java (Spring Boot / plain Java) | Backend node 3 |
| `minicord-rmi-registry` | Java RMI Registry | RMI service registry |
| `minicord-db-primary` | PostgreSQL / MySQL | Primary database |
| `minicord-db-replica` | PostgreSQL / MySQL | Read replica |
| `minicord-coordinator` | Java | Election + Clock sync coordinator |

**Total: 9 containers** (can be scaled to more)

---

## 4. Distributed Computing Topics — Detailed Breakdown

---

### 4.1 Java RMI / RPC

#### What It Is
Java Remote Method Invocation (RMI) allows a Java object running in one JVM (container) to invoke methods on an object in another JVM (container) as if it were a local call.

#### How MiniCord Uses It

**Architecture:**
- Each backend server exposes RMI interfaces for inter-service communication
- An RMI Registry container acts as the naming service
- Servers use RMI to communicate with each other (not HTTP) for internal operations

**RMI Interfaces Exposed:**

```java
// Interface for message replication across servers
public interface MessageReplicationService extends Remote {
    void replicateMessage(MessageDTO message) throws RemoteException;
    List<MessageDTO> getMessagesSince(long timestamp) throws RemoteException;
    boolean healthCheck() throws RemoteException;
}

// Interface for user presence synchronization
public interface PresenceService extends Remote {
    void updatePresence(String userId, String status) throws RemoteException;
    Map<String, String> getAllPresence() throws RemoteException;
}

// Interface for election communication
public interface ElectionService extends Remote {
    ElectionResponse election(int candidateId) throws RemoteException;
    void coordinator(int coordinatorId) throws RemoteException;
    boolean isAlive() throws RemoteException;
}

// Interface for clock synchronization
public interface ClockSyncService extends Remote {
    long getLocalTime() throws RemoteException;
    void adjustClock(long offset) throws RemoteException;
    LamportClock getLamportClock() throws RemoteException;
}
```

**Where RMI Is Used (Specific Use Cases):**

| Operation | RMI Call | From → To |
|---|---|---|
| Message sent on Server 1 | `replicateMessage()` | Server 1 → Server 2, 3 |
| User comes online | `updatePresence()` | Any server → All other servers |
| Election triggered | `election()`, `coordinator()` | Candidate → Higher ID servers |
| Clock sync request | `getLocalTime()` | Coordinator → All servers |
| Health check | `healthCheck()`, `isAlive()` | Coordinator → All servers |

**What to Demonstrate:**
1. Show RMI registry running in its own container
2. Show servers registering their remote objects
3. Send a message and show the RMI call chain in logs (Server 1 calls `replicateMessage()` on Server 2 and 3)
4. Show that RMI works transparently across Docker containers (different JVMs, different IPs)
5. Show what happens when RMI call fails (`RemoteException`) — ties into fault tolerance

---

### 4.2 Multithreading

#### What It Is
Using multiple threads within a single process to handle concurrent operations efficiently.

#### How MiniCord Uses It

**Each backend server is heavily multithreaded:**

| Thread / Thread Pool | Purpose |
|---|---|
| **WebSocket Handler Pool** | Each connected user gets a thread (or virtual thread) to handle their WebSocket connection |
| **Message Processing Thread** | Dedicated thread that processes incoming messages from a concurrent queue |
| **Replication Thread Pool** | Pool of threads that handle RMI replication calls to other servers (non-blocking) |
| **Heartbeat Thread** | Daemon thread that sends periodic heartbeats to the coordinator |
| **Clock Sync Thread** | Background thread that periodically synchronizes clocks with the coordinator |
| **Election Monitor Thread** | Thread that monitors coordinator liveness and triggers election if needed |
| **Client Connection Manager** | Thread that manages client connections, handles connect/disconnect events |

**Thread Synchronization Mechanisms Used:**

| Mechanism | Where Used |
|---|---|
| `synchronized` blocks | Lamport clock increment (critical section) |
| `ReentrantReadWriteLock` | Message store — multiple readers, single writer |
| `ConcurrentHashMap` | User presence map, active connections map |
| `BlockingQueue` | Message queue between WebSocket handler and message processor |
| `ExecutorService` | Thread pool for RMI replication calls |
| `CountDownLatch` | Waiting for all replicas to acknowledge during strong consistency writes |
| `AtomicInteger` | Lamport clock counter, election ID counter |
| `Semaphore` | Rate limiting — max N concurrent RMI calls |

**What to Demonstrate:**
1. Show multiple users connecting simultaneously — each gets their own handler thread
2. Show thread pool stats in the admin dashboard (active threads, queued tasks)
3. Show that message replication happens **concurrently** via thread pool (not sequentially)
4. Show a race condition scenario (without synchronization) and then the fix (with synchronization) for the Lamport clock
5. Show `BlockingQueue` in action — messages enqueued by WebSocket threads, dequeued by processor thread
6. Show thread dump of a running server to visualize all active threads

---

### 4.3 Clock Synchronization

#### What It Is
Ensuring all nodes in a distributed system have a consistent notion of time, using logical clocks (Lamport, Vector) and/or physical clock synchronization (Berkeley, Cristian's).

#### How MiniCord Uses It

**Two types of clocks are implemented:**

##### A) Lamport Logical Clock
- Every server maintains a Lamport clock counter
- **On send event:** increment clock, attach timestamp to message
- **On receive event:** `clock = max(local_clock, received_clock) + 1`
- Every message in MiniCord carries a Lamport timestamp
- Messages in a channel are ordered by Lamport timestamp (with tie-breaking by server ID)

##### B) Vector Clock
- Each server maintains a vector clock `[S1_time, S2_time, S3_time]`
- Used to detect **causal ordering** and **concurrent events**
- When two messages are concurrent (neither happened-before the other), the UI shows them with a special "concurrent" indicator
- Vector clocks are displayed in the admin dashboard

##### C) Berkeley Algorithm (Physical Clock Sync)
- The **coordinator node** periodically runs the Berkeley algorithm:
  1. Coordinator requests time from all servers via RMI (`getLocalTime()`)
  2. Coordinator computes the average (excluding outliers)
  3. Coordinator sends adjustment offsets to each server via RMI (`adjustClock()`)
  4. All servers adjust their local clocks
- This keeps the wall-clock times roughly synchronized across containers

**What to Demonstrate:**
1. **Lamport Clock in action:** Send messages from two different servers and show Lamport timestamps incrementing and synchronizing
2. **Vector Clock causality:** Show two concurrent messages and how vector clocks detect they are concurrent vs. causally related
3. **Berkeley Algorithm:** 
   - Show clock drift between containers (each container's time displayed in dashboard)
   - Trigger Berkeley sync
   - Show all clocks converging
4. **Clock drift simulation:** Artificially skew one server's clock and show how the system detects and corrects it
5. **Message ordering:** Show that even with network delays, messages appear in correct causal order thanks to logical clocks

**Admin Dashboard Clock Panel:**
```
┌─────────────────────────────────────────────┐
│           Clock Synchronization             │
├─────────────────────────────────────────────┤
│ Server 1:  Lamport: 42  Vector: [42, 38, 40] │
│            Physical: 10:30:05.123           │
│ Server 2:  Lamport: 38  Vector: [40, 38, 39] │
│            Physical: 10:30:05.456           │
│ Server 3:  Lamport: 40  Vector: [41, 37, 40] │
│            Physical: 10:30:04.789           │
│                                             │
│ [Run Berkeley Sync]  [Skew Clock S2 +5s]   │
│ [Show Lamport History]                       │
└─────────────────────────────────────────────┘
```

---

### 4.4 Fault Tolerance

#### What It Is
The ability of a system to continue operating correctly even when some of its components fail.

#### How MiniCord Uses It

**Failure Detection:**
- Every server sends **heartbeat** messages to the coordinator every 3 seconds via RMI
- The coordinator maintains a "last seen" timestamp for each server
- If a server misses 3 consecutive heartbeats (9 seconds), it is declared **DEAD**
- The coordinator broadcasts the failure to all remaining servers

**Fault Tolerance Mechanisms:**

| Mechanism | Description |
|---|---|
| **Heartbeat Monitoring** | Periodic liveness checks via RMI `isAlive()` |
| **Automatic Failover** | If a server dies, its connected clients are redirected to another server via the load balancer |
| **State Replication** | Messages are replicated to all servers, so no data is lost when one dies |
| **Coordinator Recovery** | If the coordinator dies, a new election is triggered (Bully/Ring) |
| **Client Reconnection** | Frontend automatically reconnects WebSocket to a different server if current one dies |
| **Database Failover** | If primary DB is down, read replica is promoted (or system works in read-only mode) |
| **Graceful Degradation** | System continues with reduced capacity rather than complete failure |

**Failure Scenarios to Simulate:**

| # | Scenario | How to Simulate | Expected Behavior |
|---|---|---|---|
| 1 | **Single server crash** | `docker stop minicord-server-2` | Clients reconnect to S1/S3, no messages lost |
| 2 | **Coordinator crash** | `docker stop minicord-coordinator` | Election triggers, new coordinator elected |
| 3 | **Network partition** | `docker network disconnect` | Servers detect partition, continue independently |
| 4 | **Database crash** | `docker stop minicord-db-primary` | System switches to replica or enters read-only mode |
| 5 | **Load balancer crash** | `docker stop minicord-lb` | Frontend shows error, manual switch to direct server connection |
| 6 | **Multiple server crash** | Stop 2 out of 3 servers | System runs on 1 server with degraded performance |
| 7 | **Server recovery** | `docker start minicord-server-2` | Server rejoins, syncs state from other servers |

**What to Demonstrate:**
1. **Kill a server:** Stop a container, show clients seamlessly reconnect, messages still flow
2. **Kill the coordinator:** Show election being triggered automatically
3. **Show heartbeat logs:** Visualize the heartbeat mechanism in real-time
4. **Recovery:** Restart a killed server and show it re-syncing state from peers
5. **Dashboard visualization:** Node goes from 🟢 to 🔴 when killed, back to 🟢 when recovered
6. **No message loss:** Send messages before, during, and after failure — all messages are preserved

---

### 4.5 Data Consistency

#### What It Is
Ensuring that all replicas/nodes in the distributed system see the same data, and defining the consistency guarantees the system provides.

#### How MiniCord Uses It

**Consistency Models Implemented (Switchable via Admin Dashboard):**

##### A) Strong Consistency (Synchronous Replication)
- When a message is sent, the receiving server replicates it to ALL other servers **synchronously**
- The server waits for **all** replicas to acknowledge before confirming to the client
- Uses `CountDownLatch` to wait for all ACKs
- **Pros:** All servers always have the same data
- **Cons:** Slower, system blocks if any server is slow/dead

##### B) Eventual Consistency (Asynchronous Replication)
- When a message is sent, the receiving server immediately confirms to the client
- Replication to other servers happens **asynchronously** in background threads
- There is a brief window where different servers may show different messages
- Uses anti-entropy gossip protocol to eventually synchronize
- **Pros:** Fast, responsive
- **Cons:** Temporary inconsistency

##### C) Quorum-Based Consistency
- Uses **Read/Write quorums** with W + R > N
- With 3 servers: W=2, R=2 (majority quorum)
- A write succeeds if at least 2 servers acknowledge
- A read queries at least 2 servers and takes the latest version
- **Pros:** Balance between speed and consistency
- **Cons:** More complex

**Conflict Resolution:**
- **Last-Writer-Wins (LWW):** When conflicts are detected (concurrent writes), the message with the higher Lamport timestamp wins
- **Vector Clock conflict detection:** If two updates are concurrent (vector clocks are incomparable), the system flags a conflict

**What to Demonstrate:**
1. **Strong vs. Eventual:** Switch between modes in the dashboard
   - In strong mode: send a message, show it appears on ALL servers simultaneously
   - In eventual mode: send a message, show the replication delay (one server has it, others don't yet, then they catch up)
2. **Quorum writes:** Send a message with W=2, show 2 servers ACK immediately, third catches up later
3. **Consistency violation:** In eventual mode, kill network to one server, send messages, show divergent state, then restore and show reconciliation
4. **Read-your-writes guarantee:** Show that the user who sent a message always sees it immediately (even in eventual mode)
5. **Dashboard metrics:** Show replication lag, consistency level indicator, conflict count

**Admin Dashboard Consistency Panel:**
```
┌─────────────────────────────────────────────┐
│           Data Consistency                   │
├─────────────────────────────────────────────┤
│ Current Mode: [Strong ▼]                    │
│                                             │
│ Server 1: 142 messages  (latest: msg-142)   │
│ Server 2: 142 messages  (latest: msg-142)   │
│ Server 3: 140 messages  (latest: msg-140)   │
│                                             │
│ Replication Lag: S3 is 2 messages behind    │
│ Conflicts Detected: 0                       │
│ Quorum Config: W=2 R=2 N=3                 │
│                                             │
│ [Switch to Eventual] [Trigger Conflict]     │
└─────────────────────────────────────────────┘
```

---

### 4.6 Bully and Ring Election Algorithms

#### What It Is
Algorithms to elect a **coordinator/leader** node in a distributed system when the current leader fails.

#### How MiniCord Uses It

**Every backend server has a unique ID:** Server 1 (ID=1), Server 2 (ID=2), Server 3 (ID=3).

**The coordinator is responsible for:**
- Running the Berkeley clock synchronization algorithm
- Monitoring heartbeats from all servers
- Making decisions about data consistency (which write is authoritative)
- Assigning tasks and managing the cluster

**When is an election triggered?**
- When a server detects that the coordinator has not sent a heartbeat for 9 seconds
- When a new server joins the cluster and has a higher ID than the current coordinator
- Manually via the admin dashboard (for demo purposes)

##### A) Bully Algorithm

**Process:**
1. Server P detects coordinator failure
2. P sends `ELECTION` message to all servers with **higher IDs** (via RMI)
3. If any higher-ID server responds with `OK` (meaning "I'll take over"), P backs off
4. If no higher-ID server responds within timeout, P declares itself coordinator
5. P sends `COORDINATOR` message to all servers (via RMI)

**Example with 3 servers (Coordinator = S3 dies):**
1. S1 detects S3 is dead → sends ELECTION to S2, S3
2. S2 responds OK to S1 (S2 > S1)
3. S3 doesn't respond (dead)
4. S2 sends ELECTION to S3 → no response
5. S2 declares itself coordinator → sends COORDINATOR to S1
6. S1 accepts S2 as new coordinator

##### B) Ring Algorithm

**Process:**
1. Server P detects coordinator failure
2. P creates an election message containing its own ID
3. P sends the message to the next alive server in the ring
4. Each server adds its own ID to the message and forwards it
5. When the message returns to the initiator (it has gone full circle), the server with the **highest ID** in the list becomes coordinator
6. A COORDINATOR message is sent around the ring

**Example with 3 servers (Ring: S1 → S2 → S3 → S1, Coordinator = S3 dies):**
1. S1 detects S3 is dead
2. S1 creates election message: [1]
3. S1 sends to next alive in ring → S2 (S3 is dead, skip to S2... or S1→S2 directly since S3 dead)
4. S2 adds its ID: [1, 2]
5. Message returns to S1 (full circle since S3 is dead)
6. Highest ID in [1, 2] = 2 → S2 is new coordinator
7. COORDINATOR(S2) sent around the ring

**What to Demonstrate:**
1. **Bully election:**
   - Show 3 servers with S3 as coordinator
   - Kill S3 (`docker stop minicord-server-3`)
   - Watch the bully algorithm execute in real-time on the dashboard
   - See S2 become the new coordinator
   - Show the ELECTION and COORDINATOR RMI messages in logs
2. **Ring election:**
   - Same scenario but using the Ring algorithm
   - Toggle between Bully and Ring in the admin dashboard
   - Show the election message traveling around the ring
3. **New node with higher ID:**
   - Start a new server S4 (ID=4)
   - Show that S4 triggers an election and becomes coordinator (Bully) or gets elected (Ring)
4. **Multiple failures:**
   - Kill S3 and S2, show S1 becomes coordinator
5. **Election visualization:**
   - Animated diagram in the dashboard showing messages flowing between nodes during election
   - Step-by-step replay of the election process

**Admin Dashboard Election Panel:**
```
┌─────────────────────────────────────────────┐
│         Leader Election                      │
├─────────────────────────────────────────────┤
│ Algorithm: [Bully ▼]                        │
│ Current Coordinator: Server 3 (ID=3) 🟢     │
│                                             │
│ Nodes:                                      │
│   S1 (ID=1) 🟢 — Follower                  │
│   S2 (ID=2) 🟢 — Follower                  │
│   S3 (ID=3) 🟢 — Coordinator               │
│                                             │
│ Election History:                           │
│   [10:30:05] S3 elected via Bully           │
│   [10:25:12] S2 elected via Ring            │
│                                             │
│ [Kill Coordinator]  [Trigger Election]      │
│ [Add New Node]      [View Election Log]     │
└─────────────────────────────────────────────┘
```

---

### 4.7 Load Balancer

#### What It Is
Distributing incoming network traffic across multiple backend servers to ensure no single server is overwhelmed.

#### How MiniCord Uses It

**Load Balancer sits between the React frontend and the backend server cluster.**

**Load Balancing Algorithms Implemented (Switchable):**

| Algorithm | Description |
|---|---|
| **Round Robin** | Requests are distributed sequentially: S1 → S2 → S3 → S1 → ... |
| **Least Connections** | Request goes to the server with fewest active connections |
| **Weighted Round Robin** | Servers have weights (e.g., S1=1, S2=2, S3=1), higher weight gets more requests |
| **Random** | Request goes to a random alive server |

**Implementation Options:**
- **Option A:** Nginx as reverse proxy (configured via `nginx.conf`)
- **Option B:** Custom Java load balancer (more control, better for demo)
- **Recommended: Option B** — a Java-based LB gives more visibility and control for demonstrations

**Health Checks:**
- LB sends periodic health checks (`GET /health`) to all backend servers
- If a server fails health check 3 times, it is marked **DOWN** and removed from the rotation
- When a server recovers, it is added back to the rotation

**WebSocket Sticky Sessions:**
- WebSocket connections require sticky sessions (a client's WS connection stays on the same server)
- LB uses session affinity based on a session cookie
- If the server dies, the WS reconnects and LB assigns a new server

**What to Demonstrate:**
1. **Round Robin distribution:** Send multiple requests, show them distributed evenly across servers
2. **Server failure handling:**
   - Kill Server 2 → LB detects failure → stops sending traffic to S2
   - All traffic goes to S1 and S3
   - Restart S2 → LB adds it back to the pool
3. **Algorithm switching:** Switch from Round Robin to Least Connections in the dashboard, show different distribution patterns
4. **Load metrics:** Show requests-per-second per server, active connections, response times
5. **Sticky sessions:** Show that a user's WebSocket stays on the same server across multiple messages
6. **Overload simulation:** Flood one server with requests, show LB adapting (Least Connections mode)

**Admin Dashboard Load Balancer Panel:**
```
┌─────────────────────────────────────────────┐
│           Load Balancer                      │
├─────────────────────────────────────────────┤
│ Algorithm: [Round Robin ▼]                  │
│                                             │
│ Server 1: 🟢 Active                        │
│   Connections: 45  |  RPS: 120  |  Avg: 23ms │
│ Server 2: 🟢 Active                        │
│   Connections: 42  |  RPS: 118  |  Avg: 25ms │
│ Server 3: 🔴 Down (missed 3 health checks) │
│   Connections: 0   |  RPS: 0    |  Avg: --  │
│                                             │
│ Total RPS: 238  |  Total Connections: 87    │
│                                             │
│ [Kill Server 2]  [Restore Server 3]         │
│ [Simulate Load]  [View Access Logs]         │
└─────────────────────────────────────────────┘
```

---

## 5. Technology Stack

| Layer | Technology | Justification |
|---|---|---|
| **Frontend** | React (Vite) | Modern, fast, component-based UI |
| **Frontend Styling** | CSS (or Tailwind) | Responsive, Discord-like dark theme |
| **Frontend State** | React Context / Zustand | Global state for user, channels, messages |
| **Frontend Real-time** | WebSocket (native / Socket.IO client) | Real-time message delivery |
| **Backend** | Java (Spring Boot or plain Java with Javalin/Spark) | Required for Java RMI |
| **RPC Mechanism** | Java RMI | Inter-service communication (required topic) |
| **REST API** | Spring Boot REST / Javalin | Frontend ↔ Backend communication |
| **WebSocket Server** | Java WebSocket (JSR 356 / Spring WebSocket) | Real-time messaging |
| **Database** | PostgreSQL (with replication) | Persistent message storage |
| **Load Balancer** | Custom Java LB / Nginx | Traffic distribution |
| **Containerization** | Docker + Docker Compose | All services containerized |
| **Networking** | Docker internal network | Service discovery, isolation |

---

## 6. Database Schema

```sql
-- Users table
CREATE TABLE users (
    id          UUID PRIMARY KEY,
    username    VARCHAR(50) UNIQUE NOT NULL,
    email       VARCHAR(100) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,  -- bcrypt hashed
    avatar_url  VARCHAR(255),
    status      VARCHAR(20) DEFAULT 'offline',  -- online, offline, idle
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Servers (Discord "servers" / guilds)
CREATE TABLE servers (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    owner_id    UUID REFERENCES users(id),
    invite_code VARCHAR(10) UNIQUE NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Channels within a server
CREATE TABLE channels (
    id          UUID PRIMARY KEY,
    server_id   UUID REFERENCES servers(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Server membership
CREATE TABLE server_members (
    user_id     UUID REFERENCES users(id),
    server_id   UUID REFERENCES servers(id),
    joined_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, server_id)
);

-- Messages
CREATE TABLE messages (
    id              UUID PRIMARY KEY,
    channel_id      UUID REFERENCES channels(id) ON DELETE CASCADE,
    author_id       UUID REFERENCES users(id),
    content         TEXT NOT NULL,
    lamport_ts      BIGINT NOT NULL,          -- Lamport timestamp
    vector_clock    JSONB,                     -- Vector clock as JSON
    server_origin   INT NOT NULL,              -- Which backend server received this message
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Election history (for dashboard display)
CREATE TABLE election_log (
    id              SERIAL PRIMARY KEY,
    algorithm       VARCHAR(10) NOT NULL,      -- BULLY or RING
    old_coordinator INT,
    new_coordinator INT,
    triggered_by    INT,
    timestamp       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    details         JSONB                      -- Step-by-step election details
);
```

---

## 7. Docker Compose Structure

```yaml
version: '3.8'

services:
  # ─── Frontend ────────────────────────────────
  frontend:
    build: ./frontend
    container_name: minicord-frontend
    ports:
      - "3000:80"
    depends_on:
      - loadbalancer
    networks:
      - minicord-net

  # ─── Load Balancer ──────────────────────────
  loadbalancer:
    build: ./loadbalancer
    container_name: minicord-lb
    ports:
      - "8080:8080"
    depends_on:
      - server-1
      - server-2
      - server-3
    networks:
      - minicord-net

  # ─── Backend Servers ────────────────────────
  server-1:
    build: ./backend
    container_name: minicord-server-1
    environment:
      - SERVER_ID=1
      - RMI_REGISTRY_HOST=rmi-registry
      - DB_HOST=db-primary
    depends_on:
      - rmi-registry
      - db-primary
    networks:
      - minicord-net

  server-2:
    build: ./backend
    container_name: minicord-server-2
    environment:
      - SERVER_ID=2
      - RMI_REGISTRY_HOST=rmi-registry
      - DB_HOST=db-primary
    depends_on:
      - rmi-registry
      - db-primary
    networks:
      - minicord-net

  server-3:
    build: ./backend
    container_name: minicord-server-3
    environment:
      - SERVER_ID=3
      - RMI_REGISTRY_HOST=rmi-registry
      - DB_HOST=db-primary
    depends_on:
      - rmi-registry
      - db-primary
    networks:
      - minicord-net

  # ─── RMI Registry ──────────────────────────
  rmi-registry:
    build: ./rmi-registry
    container_name: minicord-rmi-registry
    ports:
      - "1099:1099"
    networks:
      - minicord-net

  # ─── Database Primary ──────────────────────
  db-primary:
    image: postgres:15
    container_name: minicord-db-primary
    environment:
      POSTGRES_DB: minicord
      POSTGRES_USER: minicord
      POSTGRES_PASSWORD: minicord123
    volumes:
      - db-primary-data:/var/lib/postgresql/data
      - ./db/init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - minicord-net

  # ─── Database Replica ──────────────────────
  db-replica:
    image: postgres:15
    container_name: minicord-db-replica
    environment:
      POSTGRES_DB: minicord
      POSTGRES_USER: minicord
      POSTGRES_PASSWORD: minicord123
    depends_on:
      - db-primary
    networks:
      - minicord-net

networks:
  minicord-net:
    driver: bridge

volumes:
  db-primary-data:
```

---

## 8. API Endpoints (REST)

### Authentication
| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, returns JWT |
| POST | `/api/auth/logout` | Logout |

### Servers
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/servers` | List user's servers |
| POST | `/api/servers` | Create a server |
| POST | `/api/servers/join` | Join via invite code |
| DELETE | `/api/servers/:id` | Delete server (owner only) |

### Channels
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/servers/:id/channels` | List channels in a server |
| POST | `/api/servers/:id/channels` | Create channel |
| DELETE | `/api/channels/:id` | Delete channel |

### Messages
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/channels/:id/messages` | Get message history |
| POST | `/api/channels/:id/messages` | Send message (also broadcast via WS) |

### Admin / DC Dashboard
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/admin/nodes` | Get all node statuses |
| GET | `/api/admin/clocks` | Get clock state of all nodes |
| GET | `/api/admin/election/history` | Get election history |
| POST | `/api/admin/election/trigger` | Manually trigger election |
| POST | `/api/admin/node/:id/kill` | Simulate killing a node |
| POST | `/api/admin/node/:id/revive` | Revive a killed node |
| POST | `/api/admin/consistency/mode` | Switch consistency mode |
| GET | `/api/admin/lb/stats` | Load balancer statistics |
| POST | `/api/admin/lb/algorithm` | Switch LB algorithm |
| POST | `/api/admin/clock/skew` | Introduce clock skew on a node |
| POST | `/api/admin/clock/sync` | Trigger Berkeley sync |

### WebSocket
| Event | Direction | Description |
|---|---|---|
| `connect` | Client → Server | Client connects with auth token |
| `join_channel` | Client → Server | Client joins a channel room |
| `message` | Bidirectional | Send/receive messages |
| `presence` | Server → Client | User online/offline updates |
| `typing` | Client → Server → Clients | User typing indicator |
| `node_status` | Server → Client | Node up/down notifications (admin) |
| `election_update` | Server → Client | Election progress updates (admin) |
| `clock_update` | Server → Client | Clock sync updates (admin) |

---

## 9. Frontend Pages & Components

### Pages

| Page | Route | Description |
|---|---|---|
| **Login/Register** | `/auth` | Authentication page |
| **Main Chat** | `/` | Discord-like layout: server list → channel list → chat area |
| **DC Dashboard** | `/admin` | Distributed computing visualization panel |

### Main Chat Layout
```
┌────┬──────────┬──────────────────────────────┬──────┐
│    │          │                              │      │
│ S  │ Channels │     Chat Messages            │ User │
│ e  │          │                              │ List │
│ r  │ #general │  [User1] Hello!    10:30:05  │      │
│ v  │ #random  │  [User2] Hi there! 10:30:07  │ 🟢U1 │
│ e  │ #dev     │  [User1] How are u 10:30:10  │ 🟢U2 │
│ r  │          │                              │ ⚫U3 │
│ s  │          │                              │      │
│    │          ├──────────────────────────────┤      │
│    │          │  [Type a message...]    Send │      │
└────┴──────────┴──────────────────────────────┴──────┘
```

### DC Dashboard Layout
```
┌─────────────────────────────────────────────────────┐
│                MiniCord DC Dashboard                 │
├──────────────────┬──────────────────────────────────┤
│                  │                                  │
│  Node Topology   │   Active Panel                   │
│  (Visual Map)    │   (Selected Topic Detail)        │
│                  │                                  │
│  🟢 S1           │   [Clock Sync / Election /       │
│  🟢 S2           │    Fault Tolerance / etc.]       │
│  🔴 S3           │                                  │
│  🟢 Coordinator  │                                  │
│  🟢 LB           │                                  │
│                  │                                  │
├──────────────────┴──────────────────────────────────┤
│  Tabs: [Clocks] [Election] [Fault] [Consistency]   │
│        [Load Balancer] [RMI Logs] [Threads]        │
└─────────────────────────────────────────────────────┘
```

---

## 10. Directory Structure

```
minicord/
├── docker-compose.yml
├── .env
├── README.md
│
├── frontend/
│   ├── Dockerfile
│   ├── nginx.conf              # Serves built React app
│   ├── package.json
│   ├── vite.config.js
│   ├── public/
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       ├── index.css
│       ├── api/                # API client functions
│       │   ├── auth.js
│       │   ├── servers.js
│       │   ├── channels.js
│       │   ├── messages.js
│       │   └── admin.js        # DC dashboard API calls
│       ├── components/
│       │   ├── chat/
│       │   │   ├── ServerList.jsx
│       │   │   ├── ChannelList.jsx
│       │   │   ├── MessageArea.jsx
│       │   │   ├── MessageInput.jsx
│       │   │   ├── UserList.jsx
│       │   │   └── Message.jsx
│       │   └── dashboard/
│       │       ├── NodeTopology.jsx
│       │       ├── ClockPanel.jsx
│       │       ├── ElectionPanel.jsx
│       │       ├── FaultPanel.jsx
│       │       ├── ConsistencyPanel.jsx
│       │       ├── LoadBalancerPanel.jsx
│       │       ├── RMILogPanel.jsx
│       │       └── ThreadPanel.jsx
│       ├── hooks/
│       │   ├── useWebSocket.js
│       │   └── useAuth.js
│       ├── context/
│       │   ├── AuthContext.jsx
│       │   └── ChatContext.jsx
│       └── pages/
│           ├── AuthPage.jsx
│           ├── ChatPage.jsx
│           └── DashboardPage.jsx
│
├── backend/
│   ├── Dockerfile
│   ├── pom.xml                 # Maven build
│   └── src/main/java/com/minicord/
│       ├── MiniCordServer.java             # Main entry point
│       ├── config/
│       │   └── ServerConfig.java
│       ├── model/
│       │   ├── User.java
│       │   ├── Server.java
│       │   ├── Channel.java
│       │   └── Message.java
│       ├── api/
│       │   ├── AuthController.java
│       │   ├── ServerController.java
│       │   ├── ChannelController.java
│       │   ├── MessageController.java
│       │   └── AdminController.java        # DC admin endpoints
│       ├── websocket/
│       │   └── ChatWebSocketHandler.java
│       ├── rmi/
│       │   ├── interfaces/
│       │   │   ├── MessageReplicationService.java
│       │   │   ├── PresenceService.java
│       │   │   ├── ElectionService.java
│       │   │   └── ClockSyncService.java
│       │   └── impl/
│       │       ├── MessageReplicationServiceImpl.java
│       │       ├── PresenceServiceImpl.java
│       │       ├── ElectionServiceImpl.java
│       │       └── ClockSyncServiceImpl.java
│       ├── election/
│       │   ├── BullyAlgorithm.java
│       │   └── RingAlgorithm.java
│       ├── clock/
│       │   ├── LamportClock.java
│       │   ├── VectorClock.java
│       │   └── BerkeleySync.java
│       ├── consistency/
│       │   ├── StrongConsistency.java
│       │   ├── EventualConsistency.java
│       │   └── QuorumConsistency.java
│       ├── loadbalancer/
│       │   ├── LoadBalancer.java
│       │   ├── RoundRobinStrategy.java
│       │   ├── LeastConnectionsStrategy.java
│       │   └── HealthChecker.java
│       ├── fault/
│       │   ├── HeartbeatManager.java
│       │   ├── FailureDetector.java
│       │   └── RecoveryManager.java
│       └── threading/
│           ├── MessageProcessorThread.java
│           ├── ReplicationThreadPool.java
│           └── HeartbeatThread.java
│
├── loadbalancer/
│   ├── Dockerfile
│   └── src/main/java/com/minicord/lb/
│       ├── LoadBalancerApp.java
│       ├── RoundRobin.java
│       ├── LeastConnections.java
│       └── HealthCheck.java
│
├── rmi-registry/
│   ├── Dockerfile
│   └── start-registry.sh
│
└── db/
    └── init.sql                # Database initialization script
```

---

## 11. Simulation Controls (Admin Dashboard Buttons)

These are the actions available in the admin dashboard to trigger demos:

| Button | Action | DC Topic |
|---|---|---|
| **Kill Server N** | Stops a backend server container | Fault Tolerance, Load Balancer |
| **Revive Server N** | Restarts a stopped server | Fault Tolerance, Load Balancer |
| **Trigger Election** | Manually initiates leader election | Bully & Ring |
| **Switch Algorithm (Bully/Ring)** | Changes election algorithm | Bully & Ring |
| **Skew Clock +Ns** | Adds N seconds offset to a server's clock | Clock Synchronization |
| **Run Berkeley Sync** | Triggers the Berkeley algorithm | Clock Synchronization |
| **Switch Consistency Mode** | Changes between Strong/Eventual/Quorum | Data Consistency |
| **Trigger Conflict** | Sends simultaneous writes to create a conflict | Data Consistency |
| **Switch LB Algorithm** | Changes load balancing strategy | Load Balancer |
| **Simulate Load** | Sends burst of requests to test LB | Load Balancer |
| **Show Thread Dump** | Displays all threads in a server | Multithreading |
| **Show RMI Logs** | Displays recent RMI call logs | Java RMI |
| **Network Partition** | Isolates a server from the network | Fault Tolerance |

---

## 12. Non-Functional Requirements

| Requirement | Target |
|---|---|
| **Latency** | Messages delivered within 500ms under normal conditions |
| **Scalability** | Support 3-5 backend servers |
| **Availability** | System operational with at least 1 server alive |
| **Recovery Time** | Failed server detected within 9 seconds |
| **Election Time** | New coordinator elected within 15 seconds |
| **Clock Drift** | Clocks synchronized within 100ms after Berkeley sync |
| **Docker Startup** | Entire system up within 60 seconds via `docker-compose up` |

---

## 13. What Makes This Project Stand Out

1. **It's a real application** — not just algorithm demos. Users can actually chat.
2. **Visual dashboard** — every DC concept is visualized in real-time, not just logged to console.
3. **Interactive simulations** — professors can click buttons to trigger failures, elections, etc. during the demo.
4. **All 7 topics are interconnected** — killing a server triggers fault tolerance → which triggers election → which triggers clock re-sync → which shows consistency issues → which shows load balancer adaptation. Everything cascades naturally.
5. **Docker-native** — everything runs in containers, showing true distributed computing across isolated processes.
6. **Clean architecture** — each DC concept is in its own package, easy to explain and demonstrate.

---

## 14. Glossary

| Term | Definition |
|---|---|
| **Node** | A backend server instance (container) |
| **Coordinator/Leader** | The elected node responsible for clock sync and cluster management |
| **Heartbeat** | Periodic "I'm alive" message sent between nodes |
| **Replica** | A copy of data on another node |
| **Quorum** | The minimum number of nodes that must agree for an operation |
| **Lamport Clock** | A logical clock that provides partial ordering of events |
| **Vector Clock** | A logical clock that provides causal ordering of events |
| **Election** | The process of choosing a new coordinator when the current one fails |
| **Failover** | The process of switching to a backup when the primary fails |
| **Sticky Session** | Ensuring a client's requests always go to the same backend server |
