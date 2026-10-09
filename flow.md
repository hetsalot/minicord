# MiniCord — Development Flow & Task Checklist

> Complete, sequential task list from zero to final demo. Check off tasks as you complete them.

---

## Phase 0: Project Setup & Environment
> **Goal:** Get Docker, Java, React, and database ready to go.

- [ ] **0.1** Install prerequisites: Docker Desktop, JDK 17+, Node.js 18+, Maven, Git
- [ ] **0.2** Initialize Git repository in project root
- [ ] **0.3** Create the project directory structure (see `project.md` Section 10)
- [ ] **0.4** Create root `docker-compose.yml` with all service stubs
- [ ] **0.5** Create `.env` file with database credentials, ports, and config variables
- [ ] **0.6** Create `README.md` with project overview and startup instructions
- [ ] **0.7** Verify Docker Compose starts with empty placeholder containers: `docker-compose up --build`

---

## Phase 1: Database Layer
> **Goal:** PostgreSQL running in Docker with schema ready.

- [ ] **1.1** Write `db/init.sql` with full schema (users, servers, channels, messages, election_log, server_members)
- [ ] **1.2** Configure `db-primary` service in docker-compose (PostgreSQL 15, env vars, volume mount for init.sql)
- [ ] **1.3** Configure `db-replica` service in docker-compose (read replica)
- [ ] **1.4** Test: `docker-compose up db-primary` → connect with psql and verify tables exist
- [ ] **1.5** Set up PostgreSQL streaming replication between primary and replica
- [ ] **1.6** Test: Insert a row in primary → verify it appears in replica

---

## Phase 2: Backend — Core Server (Single Instance)
> **Goal:** One Java backend server that handles REST API + WebSocket + DB.

- [ ] **2.1** Initialize Maven project in `backend/` (pom.xml with Spring Boot / Javalin dependencies)
- [ ] **2.2** Create `Dockerfile` for backend (Java 17 base image, build with Maven, run JAR)
- [ ] **2.3** Implement `ServerConfig.java` — reads SERVER_ID, DB_HOST, RMI_REGISTRY_HOST from environment
- [ ] **2.4** Implement database connection pool (HikariCP or similar)
- [ ] **2.5** Implement `User` model and `AuthController` — register, login (JWT), logout
- [ ] **2.6** Implement `Server` model and `ServerController` — create server, list servers, join via invite code, delete
- [ ] **2.7** Implement `Channel` model and `ChannelController` — CRUD channels within a server
- [ ] **2.8** Implement `Message` model and `MessageController` — send message, get message history
- [ ] **2.9** Implement WebSocket endpoint (`ChatWebSocketHandler`) — connect with auth token, join channel room, send/receive messages, typing indicator
- [ ] **2.10** Test single server: `docker-compose up db-primary server-1` → use Postman/curl to test all REST endpoints
- [ ] **2.11** Test WebSocket: connect via browser console or wscat, send messages, verify broadcast
- [ ] **2.12** Add `/health` endpoint returning server status (for load balancer health checks)

---

## Phase 3: Frontend — React Chat UI
> **Goal:** Functional Discord-like UI that connects to the backend.

- [ ] **3.1** Initialize React project with Vite in `frontend/`: `npx -y create-vite@latest ./`
- [ ] **3.2** Install dependencies: `react-router-dom`, `zustand` (or Context API), WebSocket client
- [ ] **3.3** Create `Dockerfile` for frontend (build with Vite, serve with Nginx)
- [ ] **3.4** Create `nginx.conf` — serves static files + proxies `/api` and `/ws` to load balancer
- [ ] **3.5** Set up dark theme CSS (Discord-like: dark background, sidebar, message area)
- [ ] **3.6** Implement `AuthContext.jsx` — JWT storage, login/logout state
- [ ] **3.7** Implement `AuthPage.jsx` — Login and Register forms
- [ ] **3.8** Test: Register a user, login, verify JWT is stored
- [ ] **3.9** Implement `ServerList.jsx` — left sidebar with server icons
- [ ] **3.10** Implement `ChannelList.jsx` — channel sidebar within a server
- [ ] **3.11** Implement `MessageArea.jsx` — scrollable message list with history loading
- [ ] **3.12** Implement `Message.jsx` — single message component (avatar, username, content, timestamp)
- [ ] **3.13** Implement `MessageInput.jsx` — text input with send button
- [ ] **3.14** Implement `UserList.jsx` — right sidebar showing online/offline users
- [ ] **3.15** Implement `useWebSocket.js` hook — connects to backend WS, handles events
- [ ] **3.16** Wire everything together in `ChatPage.jsx` — full Discord-like layout
- [ ] **3.17** Test end-to-end: Open 2 browser tabs, login as different users, send messages, verify real-time delivery
- [ ] **3.18** Add create server modal, join server modal, create channel modal
- [ ] **3.19** Polish UI: smooth scrolling, message grouping, timestamps, loading states

---

## Phase 4: Multi-Server + RMI (Topic 1: Java RMI/RPC)
> **Goal:** 3 backend servers communicating via Java RMI.

- [ ] **4.1** Create `rmi-registry/` service — Dockerfile that starts `rmiregistry` on port 1099
- [ ] **4.2** Define RMI interfaces in `rmi/interfaces/`:
  - [ ] **4.2.1** `MessageReplicationService` — `replicateMessage()`, `getMessagesSince()`, `healthCheck()`
  - [ ] **4.2.2** `PresenceService` — `updatePresence()`, `getAllPresence()`
  - [ ] **4.2.3** `ElectionService` — `election()`, `coordinator()`, `isAlive()`
  - [ ] **4.2.4** `ClockSyncService` — `getLocalTime()`, `adjustClock()`, `getLamportClock()`
- [ ] **4.3** Implement `MessageReplicationServiceImpl` — receives messages from other servers, stores locally
- [ ] **4.4** Implement `PresenceServiceImpl` — syncs user online/offline status across servers
- [ ] **4.5** On server startup: register all RMI services with the RMI registry
- [ ] **4.6** On server startup: look up other servers' RMI services from the registry
- [ ] **4.7** When a message is received via REST/WS: replicate to other servers via `replicateMessage()` RMI call
- [ ] **4.8** Duplicate `server-1` config in docker-compose for `server-2` (SERVER_ID=2) and `server-3` (SERVER_ID=3)
- [ ] **4.9** Test: `docker-compose up` with all 3 servers + RMI registry → send message on S1, verify it appears on S2 and S3 via direct API call
- [ ] **4.10** Add RMI call logging — log every outgoing and incoming RMI call with timestamp, method name, source, destination

---

## Phase 5: Multithreading (Topic 2)
> **Goal:** Explicit multithreaded architecture in each backend server.

- [ ] **5.1** Implement `MessageProcessorThread` — dedicated thread consuming from `BlockingQueue<Message>`
- [ ] **5.2** Modify WebSocket handler to enqueue messages to the `BlockingQueue` instead of processing inline
- [ ] **5.3** Implement `ReplicationThreadPool` — `ExecutorService` with fixed thread pool for RMI replication calls
- [ ] **5.4** Modify message replication to use the thread pool (submit tasks, non-blocking)
- [ ] **5.5** Implement `HeartbeatThread` — daemon thread sending periodic heartbeats
- [ ] **5.6** Add `ReentrantReadWriteLock` to the in-memory message cache (multiple readers, single writer)
- [ ] **5.7** Add `ConcurrentHashMap` for user presence map and active WebSocket connections
- [ ] **5.8** Add `AtomicInteger` for Lamport clock counter
- [ ] **5.9** Add `Semaphore` to rate-limit concurrent RMI calls (e.g., max 5 at a time)
- [ ] **5.10** Implement `/api/admin/threads` endpoint — returns thread dump (thread name, state, stack trace)
- [ ] **5.11** Test: Connect 5+ users simultaneously, verify thread pool usage in logs
- [ ] **5.12** Test: Show thread dump via admin endpoint, identify all custom threads

---

## Phase 6: Clock Synchronization (Topic 3)
> **Goal:** Lamport clocks, vector clocks, and Berkeley algorithm working across all servers.

- [ ] **6.1** Implement `LamportClock.java` — increment on send, update on receive (`max + 1`), thread-safe with `AtomicInteger`
- [ ] **6.2** Attach Lamport timestamp to every message sent (stored in DB `lamport_ts` column)
- [ ] **6.3** On receiving a replicated message: update local Lamport clock with `max(local, received) + 1`
- [ ] **6.4** Implement `VectorClock.java` — array of N counters, increment own index on send, merge on receive
- [ ] **6.5** Attach vector clock to every message (stored in DB `vector_clock` JSONB column)
- [ ] **6.6** Implement vector clock comparison: `happenedBefore()`, `isConcurrent()`, `merge()`
- [ ] **6.7** Order messages in a channel by Lamport timestamp (with server ID tie-breaking)
- [ ] **6.8** Implement `BerkeleySync.java`:
  - [ ] **6.8.1** Coordinator requests time from all servers via `getLocalTime()` RMI
  - [ ] **6.8.2** Coordinator computes average time (excluding outliers > 1 std deviation)
  - [ ] **6.8.3** Coordinator sends offsets to each server via `adjustClock()` RMI
  - [ ] **6.8.4** Each server adjusts its local clock
- [ ] **6.9** Implement `ClockSyncServiceImpl` — RMI implementation for clock operations
- [ ] **6.10** Add `/api/admin/clocks` endpoint — returns Lamport, vector clock, and physical time for all servers
- [ ] **6.11** Add `/api/admin/clock/skew` endpoint — artificially skews a server's clock by N seconds
- [ ] **6.12** Add `/api/admin/clock/sync` endpoint — triggers Berkeley sync
- [ ] **6.13** Test: Send messages from different servers, verify Lamport timestamps are consistent
- [ ] **6.14** Test: Skew a clock, run Berkeley sync, verify clocks converge
- [ ] **6.15** Test: Create concurrent messages, verify vector clock detects concurrency

---

## Phase 7: Leader Election — Bully & Ring (Topic 6)
> **Goal:** Both election algorithms implemented, switchable, and visualized.

- [ ] **7.1** Implement `BullyAlgorithm.java`:
  - [ ] **7.1.1** `startElection()` — send ELECTION to all higher-ID servers via RMI
  - [ ] **7.1.2** Handle ELECTION response — if OK received from higher, back off
  - [ ] **7.1.3** Handle timeout — if no OK received, declare self as coordinator
  - [ ] **7.1.4** `announceCoordinator()` — send COORDINATOR message to all servers via RMI
- [ ] **7.2** Implement `RingAlgorithm.java`:
  - [ ] **7.2.1** `startElection()` — create election message with own ID, send to next alive in ring
  - [ ] **7.2.2** `forwardElection()` — add own ID to message, forward to next
  - [ ] **7.2.3** `completeElection()` — when message returns to initiator, pick highest ID
  - [ ] **7.2.4** `announceCoordinator()` — send COORDINATOR around the ring
- [ ] **7.3** Implement `ElectionServiceImpl` — RMI implementation for election messages
- [ ] **7.4** Wire election trigger to failure detection: when coordinator heartbeat times out, start election
- [ ] **7.5** Store election results in `election_log` table
- [ ] **7.6** Add `/api/admin/election/trigger` endpoint — manually triggers election
- [ ] **7.7** Add `/api/admin/election/algorithm` endpoint — switch between Bully and Ring
- [ ] **7.8** Add `/api/admin/election/history` endpoint — returns election log
- [ ] **7.9** Broadcast election progress to admin dashboard via WebSocket (`election_update` events)
- [ ] **7.10** Test Bully: Kill coordinator (highest ID server), verify next-highest becomes coordinator
- [ ] **7.11** Test Ring: Kill coordinator, verify ring election completes
- [ ] **7.12** Test: Add a new node with higher ID, verify it triggers election and becomes coordinator
- [ ] **7.13** Test: Kill multiple nodes, verify election still works with remaining nodes
- [ ] **7.14** Test: Switch between Bully and Ring, verify both produce correct results

---

## Phase 8: Fault Tolerance (Topic 4)
> **Goal:** System survives node failures gracefully.

- [ ] **8.1** Implement `HeartbeatManager.java` — manages heartbeat sending and tracking
  - [ ] **8.1.1** Each server sends heartbeat to coordinator every 3 seconds
  - [ ] **8.1.2** Coordinator tracks "last seen" timestamp for each server
- [ ] **8.2** Implement `FailureDetector.java`:
  - [ ] **8.2.1** If server misses 3 consecutive heartbeats (9s), mark as DEAD
  - [ ] **8.2.2** Broadcast failure notification to all alive servers
  - [ ] **8.2.3** If coordinator is dead, trigger election (from Phase 7)
- [ ] **8.3** Implement `RecoveryManager.java`:
  - [ ] **8.3.1** When a server comes back online, it contacts other servers for state sync
  - [ ] **8.3.2** Uses `getMessagesSince(lastKnownTimestamp)` RMI to catch up on missed messages
  - [ ] **8.3.3** Re-registers RMI services
  - [ ] **8.3.4** Notifies coordinator it is alive
- [ ] **8.4** Implement client-side reconnection in `useWebSocket.js`:
  - [ ] **8.4.1** If WebSocket disconnects, attempt reconnect with exponential backoff
  - [ ] **8.4.2** On reconnect, may connect to a different server (via load balancer)
  - [ ] **8.4.3** Fetch missed messages since last received message ID
- [ ] **8.5** Add `/api/admin/node/:id/kill` endpoint — gracefully stops a server's services (simulates crash)
- [ ] **8.6** Add `/api/admin/node/:id/revive` endpoint — restarts a server's services
- [ ] **8.7** Add `/api/admin/nodes` endpoint — returns all node statuses (ALIVE, DEAD, RECOVERING)
- [ ] **8.8** Broadcast node status changes to dashboard via WebSocket (`node_status` events)
- [ ] **8.9** Test: Kill S2, verify clients reconnect to S1/S3, messages still flow
- [ ] **8.10** Test: Kill coordinator, verify election + new coordinator takes over heartbeat monitoring
- [ ] **8.11** Test: Revive S2, verify it re-syncs messages and rejoins the cluster
- [ ] **8.12** Test: Kill 2 servers, verify system runs on remaining 1 server
- [ ] **8.13** Test: Kill all servers, restart all, verify system recovers fully from DB

---

## Phase 9: Data Consistency (Topic 5)
> **Goal:** Switchable consistency models with visible differences.

- [ ] **9.1** Implement `StrongConsistency.java`:
  - [ ] **9.1.1** On message write: replicate to ALL servers synchronously via RMI
  - [ ] **9.1.2** Use `CountDownLatch` to wait for all ACKs
  - [ ] **9.1.3** Only confirm to client after all replicas ACK
  - [ ] **9.1.4** Timeout after 5s — if any server doesn't ACK, return error
- [ ] **9.2** Implement `EventualConsistency.java`:
  - [ ] **9.2.1** On message write: immediately confirm to client
  - [ ] **9.2.2** Replicate asynchronously via thread pool
  - [ ] **9.2.3** Implement anti-entropy: periodic background sync between servers
  - [ ] **9.2.4** Track replication lag per server
- [ ] **9.3** Implement `QuorumConsistency.java`:
  - [ ] **9.3.1** Configure W (write quorum) and R (read quorum) such that W + R > N
  - [ ] **9.3.2** On write: wait for W servers to ACK
  - [ ] **9.3.3** On read: query R servers, return latest version
  - [ ] **9.3.4** Version tracking per message for conflict detection
- [ ] **9.4** Implement conflict detection using vector clocks:
  - [ ] **9.4.1** Detect concurrent writes (incomparable vector clocks)
  - [ ] **9.4.2** Resolve using Last-Writer-Wins (highest Lamport timestamp)
- [ ] **9.5** Add `/api/admin/consistency/mode` endpoint — switch between Strong/Eventual/Quorum
- [ ] **9.6** Add `/api/admin/consistency/status` endpoint — returns per-server message counts, replication lag, conflicts
- [ ] **9.7** Test Strong: Send message, verify all servers have it simultaneously
- [ ] **9.8** Test Eventual: Send message, query each server individually, show lag and eventual convergence
- [ ] **9.9** Test Quorum: Send message with W=2, verify 2 servers have it, third catches up
- [ ] **9.10** Test Conflict: Partition network, send messages on both sides, restore, show conflict resolution

---

## Phase 10: Load Balancer (Topic 7)
> **Goal:** Custom load balancer distributing traffic across backend servers.

- [ ] **10.1** Create `loadbalancer/` Maven project with HTTP proxy + WebSocket proxy
- [ ] **10.2** Create `Dockerfile` for load balancer
- [ ] **10.3** Implement `RoundRobin.java` — sequential distribution
- [ ] **10.4** Implement `LeastConnections.java` — route to server with fewest connections
- [ ] **10.5** Implement `WeightedRoundRobin.java` — weighted distribution (configurable weights)
- [ ] **10.6** Implement `HealthCheck.java`:
  - [ ] **10.6.1** Periodic `GET /health` to each backend server
  - [ ] **10.6.2** If 3 consecutive failures, mark server as DOWN
  - [ ] **10.6.3** If server recovers, mark as UP and add back to pool
- [ ] **10.7** Implement sticky sessions for WebSocket connections (session cookie / client ID)
- [ ] **10.8** Implement request logging — log each request with target server, response time, status
- [ ] **10.9** Add `/api/admin/lb/stats` endpoint — returns per-server request counts, active connections, avg response time
- [ ] **10.10** Add `/api/admin/lb/algorithm` endpoint — switch LB algorithm at runtime
- [ ] **10.11** Update `frontend/nginx.conf` to proxy through the load balancer
- [ ] **10.12** Test Round Robin: Send 9 requests, verify 3 go to each server
- [ ] **10.13** Test Least Connections: Create uneven connections, verify new requests go to least loaded
- [ ] **10.14** Test Server Failure: Kill S2, verify LB stops routing to it, traffic goes to S1/S3
- [ ] **10.15** Test Recovery: Revive S2, verify LB adds it back to pool
- [ ] **10.16** Test Sticky Sessions: Verify WebSocket stays on same server across reconnects

---

## Phase 11: DC Admin Dashboard (Frontend)
> **Goal:** React dashboard to visualize and control all DC topics.

- [ ] **11.1** Create `DashboardPage.jsx` with tab navigation (7 tabs, one per topic)
- [ ] **11.2** Implement `NodeTopology.jsx` — visual map of all nodes with live status (🟢/🔴)
- [ ] **11.3** Implement `ClockPanel.jsx`:
  - [ ] **11.3.1** Display Lamport clock, vector clock, physical time per server
  - [ ] **11.3.2** "Skew Clock" button + seconds input
  - [ ] **11.3.3** "Run Berkeley Sync" button
  - [ ] **11.3.4** Real-time clock updates via WebSocket
- [ ] **11.4** Implement `ElectionPanel.jsx`:
  - [ ] **11.4.1** Show current coordinator and all node roles
  - [ ] **11.4.2** Algorithm toggle (Bully/Ring)
  - [ ] **11.4.3** "Kill Coordinator" and "Trigger Election" buttons
  - [ ] **11.4.4** Election history log
  - [ ] **11.4.5** Animated election visualization (messages flowing between nodes)
- [ ] **11.5** Implement `FaultPanel.jsx`:
  - [ ] **11.5.1** Kill/Revive buttons for each server
  - [ ] **11.5.2** Heartbeat status indicators
  - [ ] **11.5.3** Failure/recovery event log
  - [ ] **11.5.4** Network partition simulation controls
- [ ] **11.6** Implement `ConsistencyPanel.jsx`:
  - [ ] **11.6.1** Consistency mode selector (Strong/Eventual/Quorum)
  - [ ] **11.6.2** Per-server message count display
  - [ ] **11.6.3** Replication lag indicator
  - [ ] **11.6.4** Conflict counter and details
- [ ] **11.7** Implement `LoadBalancerPanel.jsx`:
  - [ ] **11.7.1** Algorithm selector (Round Robin/Least Connections/Weighted)
  - [ ] **11.7.2** Per-server stats (connections, RPS, latency)
  - [ ] **11.7.3** Server health indicators
  - [ ] **11.7.4** "Simulate Load" button
- [ ] **11.8** Implement `RMILogPanel.jsx`:
  - [ ] **11.8.1** Real-time log of RMI calls (timestamp, method, source, destination, result)
  - [ ] **11.8.2** Filter by method type
- [ ] **11.9** Implement `ThreadPanel.jsx`:
  - [ ] **11.9.1** Thread list per server (name, state, info)
  - [ ] **11.9.2** Thread pool stats (active, queued, completed)
- [ ] **11.10** Connect all panels to admin WebSocket for real-time updates
- [ ] **11.11** Add route `/admin` in React Router for dashboard
- [ ] **11.12** Style the dashboard with dark theme matching Discord aesthetic

---

## Phase 12: Integration & Docker Compose Finalization
> **Goal:** All services running together in Docker Compose.

- [ ] **12.1** Finalize `docker-compose.yml` with all 9+ services
- [ ] **12.2** Set up Docker networking — all services on `minicord-net` bridge network
- [ ] **12.3** Configure proper service dependencies (`depends_on`)
- [ ] **12.4** Set up health checks in docker-compose for critical services
- [ ] **12.5** Create a `Makefile` or shell script for common operations:
  - [ ] `make up` — start everything
  - [ ] `make down` — stop everything
  - [ ] `make logs` — view logs
  - [ ] `make kill-server N` — stop server N
  - [ ] `make revive-server N` — start server N
- [ ] **12.6** Test full startup: `docker-compose up --build` → all services healthy
- [ ] **12.7** Test full workflow: Register → Login → Create Server → Create Channel → Send Messages → View in Dashboard
- [ ] **12.8** Verify inter-container RMI communication works
- [ ] **12.9** Verify load balancer routes correctly to all backend servers

---

## Phase 13: End-to-End Testing & Demo Preparation
> **Goal:** Rehearse all demo scenarios, fix bugs, polish.

- [ ] **13.1** Demo Scenario 1: **Normal Operation**
  - Start system, register 2 users, create server, create channels, exchange messages
- [ ] **13.2** Demo Scenario 2: **RMI Showcase**
  - Send a message, show RMI logs showing replication calls between servers
- [ ] **13.3** Demo Scenario 3: **Multithreading Showcase**
  - Connect 5 users, show thread dump, show thread pool stats
- [ ] **13.4** Demo Scenario 4: **Clock Sync Showcase**
  - Show Lamport timestamps on messages, skew a clock, run Berkeley sync, show convergence
- [ ] **13.5** Demo Scenario 5: **Fault Tolerance Showcase**
  - Kill a server, show clients reconnect, kill coordinator, show election, revive server, show re-sync
- [ ] **13.6** Demo Scenario 6: **Consistency Showcase**
  - Switch between Strong/Eventual, show replication lag in Eventual, show simultaneous delivery in Strong
- [ ] **13.7** Demo Scenario 7: **Election Showcase**
  - Trigger Bully election, then Ring election, show animated visualization
- [ ] **13.8** Demo Scenario 8: **Load Balancer Showcase**
  - Show Round Robin distribution, kill a server, show traffic redistribution, switch algorithm
- [ ] **13.9** Demo Scenario 9: **Cascading Failure**
  - Kill coordinator → triggers election → new coordinator runs Berkeley sync → consistency check
- [ ] **13.10** Fix all bugs found during demo rehearsal
- [ ] **13.11** Create demo script document with exact steps and talking points
- [ ] **13.12** Record backup video of the demo in case of live demo issues

---

## Phase 14: Documentation & Submission
> **Goal:** Clean documentation, comments, and final deliverables.

- [ ] **14.1** Write `README.md` with:
  - [ ] Project description
  - [ ] Architecture diagram
  - [ ] Prerequisites & setup instructions
  - [ ] How to run (`docker-compose up`)
  - [ ] How to access (frontend URL, dashboard URL)
  - [ ] DC topics covered and where to find them in code
- [ ] **14.2** Add inline comments to all DC-related code (election algorithms, clock sync, consistency, etc.)
- [ ] **14.3** Create presentation slides covering all 7 DC topics with screenshots
- [ ] **14.4** Final `docker-compose up --build` from clean state — verify everything works
- [ ] **14.5** Push to Git repository
- [ ] **14.6** Submit

---

## Summary: Phase Dependencies

```
Phase 0 (Setup)
    │
    ▼
Phase 1 (Database)
    │
    ▼
Phase 2 (Single Backend Server)
    │
    ├──────────────────────┐
    ▼                      ▼
Phase 3 (React Frontend)  Phase 4 (Multi-Server + RMI)
    │                      │
    │                      ├── Phase 5 (Multithreading)
    │                      ├── Phase 6 (Clock Sync)
    │                      ├── Phase 7 (Election)
    │                      ├── Phase 8 (Fault Tolerance)
    │                      ├── Phase 9 (Consistency)
    │                      └── Phase 10 (Load Balancer)
    │                           │
    └───────────┬───────────────┘
                ▼
         Phase 11 (DC Dashboard)
                │
                ▼
         Phase 12 (Integration)
                │
                ▼
         Phase 13 (Testing & Demo)
                │
                ▼
         Phase 14 (Documentation)
```

---

## Time Estimates (Rough)

| Phase | Estimated Time |
|---|---|
| Phase 0: Setup | 1 day |
| Phase 1: Database | 1 day |
| Phase 2: Backend Core | 3-4 days |
| Phase 3: React Frontend | 3-4 days |
| Phase 4: Multi-Server + RMI | 2-3 days |
| Phase 5: Multithreading | 1-2 days |
| Phase 6: Clock Sync | 2-3 days |
| Phase 7: Bully & Ring | 2-3 days |
| Phase 8: Fault Tolerance | 2-3 days |
| Phase 9: Data Consistency | 2-3 days |
| Phase 10: Load Balancer | 2-3 days |
| Phase 11: DC Dashboard | 3-4 days |
| Phase 12: Integration | 2 days |
| Phase 13: Testing & Demo | 2 days |
| Phase 14: Documentation | 1 day |
| **Total** | **~28-37 days** |
