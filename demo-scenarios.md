# MiniCord — Demo Scenarios & Simulation Script

> Step-by-step demo script for presenting all 7 DC topics. Each scenario includes exact actions, expected outcomes, and talking points.

---

## Pre-Demo Setup

```bash
# Start the entire system
docker-compose up --build -d

# Wait for all services to be healthy (~30-60 seconds)
docker-compose ps  # Verify all containers are "Up"

# Open browser tabs:
# Tab 1: http://localhost:3000 (User 1 — Alice)
# Tab 2: http://localhost:3000 (User 2 — Bob)  
# Tab 3: http://localhost:3000/admin (DC Dashboard)
```

**Pre-register users:**
1. Register "Alice" in Tab 1
2. Register "Bob" in Tab 2
3. Create a server "DC-Demo-Server" with Alice
4. Bob joins via invite code
5. Create channels: #general, #testing
6. Send a few test messages to verify everything works

---

## Demo Scenario 1: Java RMI/RPC (5 minutes)

### Setup
- Open DC Dashboard → **RMI Logs** tab

### Actions

| Step | Action | What to Show |
|---|---|---|
| 1 | Alice sends "Hello from Alice!" in #general | Watch the RMI Logs panel |
| 2 | Point to RMI log entry | `[10:30:05] S1 → S2: replicateMessage(msg-abc)` |
| 3 | Point to second RMI log entry | `[10:30:05] S1 → S3: replicateMessage(msg-abc)` |
| 4 | Explain the flow | "Alice's message hit Server 1 via the load balancer. Server 1 then used Java RMI to replicate this message to Server 2 and Server 3. These are remote method invocations — calling a Java method on a different JVM running in a completely different Docker container." |
| 5 | Show RMI Registry | `docker logs minicord-rmi-registry` — show registered services |
| 6 | Show RMI interfaces in code | Open `MessageReplicationService.java` — show the `extends Remote` interface |

### Talking Points
- "RMI Registry is running in its own container on port 1099"
- "Each server registers 4 RMI services: MessageReplication, Presence, Election, ClockSync"
- "RMI handles serialization, network transport, and deserialization — we call methods as if they're local"
- "If an RMI call fails (RemoteException), our fault tolerance kicks in"

---

## Demo Scenario 2: Multithreading (5 minutes)

### Setup
- Open DC Dashboard → **Threads** tab
- Have Alice and Bob both connected

### Actions

| Step | Action | What to Show |
|---|---|---|
| 1 | View Thread Panel for Server 1 | Show list of threads: `ws-handler-1`, `ws-handler-2`, `msg-processor`, `heartbeat`, `clock-sync`, `replication-pool-1..3` |
| 2 | Alice and Bob send messages rapidly | Watch thread pool stats update: active threads increase |
| 3 | Show thread pool stats | "Active: 2/5, Queued: 0, Completed: 47" |
| 4 | Point out the BlockingQueue | "WebSocket handlers ENQUEUE messages, the MessageProcessor thread DEQUEUES and processes them" |
| 5 | Show synchronization in code | Open `LamportClock.java` — show `AtomicInteger` and `synchronized` block |
| 6 | Show concurrent map usage | Open code showing `ConcurrentHashMap` for presence |

### Talking Points
- "Each connected user gets their own WebSocket handler thread"
- "Message replication uses a thread POOL — calls to Server 2 and 3 happen in PARALLEL, not sequentially"
- "We use BlockingQueue as a producer-consumer pattern — WebSocket threads produce, processor thread consumes"
- "Critical sections like Lamport clock updates use AtomicInteger for thread safety"
- "We use ReadWriteLock for the message cache — multiple readers, single writer"

---

## Demo Scenario 3: Clock Synchronization (7 minutes)

### Setup
- Open DC Dashboard → **Clocks** tab

### Actions

| Step | Action | What to Show |
|---|---|---|
| 1 | Show current clock state | All 3 servers' Lamport clocks, vector clocks, physical times |
| 2 | Alice sends "Message 1" | Lamport clock on S1 increments (e.g., 42 → 43) |
| 3 | Bob sends "Message 2" | Lamport clock on S2 updates to max(S2_local, received) + 1 |
| 4 | Show messages in #general | Each message has a Lamport timestamp displayed |
| 5 | Show vector clocks | `S1: [43, 38, 40]`, `S2: [42, 39, 40]` — explain what each index means |
| 6 | **Skew Clock:** Click "Skew S2 +5s" | S2's physical time jumps ahead by 5 seconds |
| 7 | Show clock panel | S2's time is now 5 seconds ahead of S1 and S3 |
| 8 | **Berkeley Sync:** Click "Run Berkeley Sync" | Watch all physical clocks converge |
| 9 | Show the sync result | All servers now within ~100ms of each other |
| 10 | Explain the algorithm | "Coordinator requested time from all 3 servers, computed average, sent offsets" |

### Talking Points
- "Lamport clocks give us a partial ordering — if event A happened before B, then L(A) < L(B)"
- "Vector clocks give us causal ordering — we can detect if two events are truly concurrent"
- "Berkeley algorithm is a physical clock sync — no single time server, democratic averaging"
- "Even though Docker containers share the host clock, we simulate independent clocks for the demo"
- "Clock synchronization is critical for message ordering in a distributed chat app"

### Concurrent Event Demo (Bonus)
| Step | Action |
|---|---|
| 1 | Disconnect S1 and S2 from each other (network partition) |
| 2 | Alice (on S1) sends "Hello from partition A" |
| 3 | Bob (on S2) sends "Hello from partition B" |
| 4 | Restore connection |
| 5 | Show that vector clocks detected these as CONCURRENT events (neither happened-before the other) |

---

## Demo Scenario 4: Fault Tolerance (8 minutes)

### Setup
- Open DC Dashboard → **Fault Tolerance** tab
- All 3 servers showing 🟢

### Actions — Single Server Failure

| Step | Action | What to Show |
|---|---|---|
| 1 | Show all servers healthy | 3 green dots in the topology |
| 2 | Show Alice chatting on S1 | Messages flowing normally |
| 3 | **Kill Server 2:** Click "Kill Server 2" | S2 goes 🔴, heartbeat missed notifications appear |
| 4 | Wait 9 seconds | Dashboard shows "Server 2 declared DEAD (missed 3 heartbeats)" |
| 5 | Alice sends "Still chatting!" | Message is replicated to S1 and S3 only (S2 skipped) |
| 6 | Show that chat still works | No interruption for users |
| 7 | **Revive Server 2:** Click "Revive Server 2" | S2 starts recovering |
| 8 | Show recovery log | "S2 syncing 5 missed messages from S1..." |
| 9 | S2 goes 🟢 | "Server 2 recovered, 5 messages synced" |

### Actions — Coordinator Failure (ties into Election demo)

| Step | Action | What to Show |
|---|---|---|
| 1 | Show S3 as coordinator | Dashboard shows "Coordinator: Server 3" |
| 2 | **Kill Server 3** (coordinator) | S3 goes 🔴 |
| 3 | Wait for detection | "Coordinator S3 unreachable!" |
| 4 | Watch election trigger automatically | Election visualization starts (next demo) |
| 5 | New coordinator elected | S2 becomes coordinator |
| 6 | Chat continues | Users unaffected |

### Talking Points
- "Heartbeats are sent every 3 seconds — 3 missed heartbeats (9s) = failure detection"
- "When a server dies, connected clients reconnect to another server via the load balancer"
- "No messages are lost because they're replicated to ALL servers"
- "When a server recovers, it pulls missed messages from peers using RMI"
- "If the coordinator dies, that's the worst case — but we handle it with leader election"

---

## Demo Scenario 5: Bully & Ring Election (8 minutes)

### Setup
- Open DC Dashboard → **Election** tab
- Ensure S3 is coordinator (highest ID = 3)

### Actions — Bully Algorithm

| Step | Action | What to Show |
|---|---|---|
| 1 | Set algorithm to "Bully" | Algorithm dropdown shows "Bully" |
| 2 | Show initial state | S3 = Coordinator, S1 & S2 = Followers |
| 3 | **Kill Coordinator (S3)** | Click "Kill Coordinator" |
| 4 | Watch election animation | S1 sends ELECTION to S2 ✓, S1 sends ELECTION to S3 ✗ (dead) |
| 5 | S2 responds OK to S1 | S1 backs off |
| 6 | S2 sends ELECTION to S3 | No response (dead) |
| 7 | S2 declares self coordinator | S2 sends COORDINATOR to S1 |
| 8 | Show result | "New Coordinator: Server 2 (via Bully Algorithm)" |
| 9 | Verify | S2 starts running Berkeley sync, S2 monitors heartbeats |

### Actions — Ring Algorithm

| Step | Action | What to Show |
|---|---|---|
| 1 | First, revive S3 | S3 comes back, triggers election, becomes coordinator again (highest ID) |
| 2 | Set algorithm to "Ring" | Algorithm dropdown shows "Ring" |
| 3 | Show ring topology | S1 → S2 → S3 → S1 (circular) |
| 4 | **Kill Coordinator (S3)** | Click "Kill Coordinator" |
| 5 | Watch ring election | S1 creates msg [1], sends to S2 (skip dead S3) |
| 6 | S2 receives [1], adds self | Message becomes [1, 2], sent to S1 (skip dead S3) |
| 7 | S1 receives [1, 2] — full circle | Max(1, 2) = 2, S2 is coordinator |
| 8 | S1 sends COORDINATOR(S2) around ring | S2 confirms |
| 9 | Show result | "New Coordinator: Server 2 (via Ring Algorithm)" |

### Actions — New Higher-ID Node

| Step | Action | What to Show |
|---|---|---|
| 1 | Current coordinator: S2 | After S3 was killed |
| 2 | **Revive S3** (ID=3, higher than S2) | S3 starts up |
| 3 | Watch auto-election | S3 has higher ID, triggers election |
| 4 | S3 becomes coordinator | Natural outcome of both Bully and Ring |
| 5 | Show election history log | List of all elections with timestamps and algorithms |

### Talking Points
- "Bully: always elect the highest-ID alive node. Quick but more messages."
- "Ring: election message travels in a ring, most democratic. Takes longer but fewer messages."
- "Both algorithms give the same result — the highest-ID alive node becomes coordinator"
- "Election happens automatically when coordinator failure is detected"
- "The coordinator is critical — it runs clock sync, monitors heartbeats, and manages consistency"

---

## Demo Scenario 6: Data Consistency (7 minutes)

### Setup
- Open DC Dashboard → **Consistency** tab
- Start with "Strong" consistency mode

### Actions — Strong Consistency

| Step | Action | What to Show |
|---|---|---|
| 1 | Mode: "Strong Consistency" | Dashboard shows Strong mode |
| 2 | Alice sends "Test Strong" | Message sends (might be slightly slower) |
| 3 | Show per-server message count | S1: 50, S2: 50, S3: 50 — all identical ✅ |
| 4 | Explain | "The server waited for ALL replicas to acknowledge before confirming" |

### Actions — Switch to Eventual Consistency

| Step | Action | What to Show |
|---|---|---|
| 1 | Switch to "Eventual" mode | Click dropdown → Eventual |
| 2 | Alice sends "Test Eventual" rapidly (3-4 messages) | Messages send instantly (faster!) |
| 3 | Immediately check per-server counts | S1: 54, S2: 52, S3: 51 — DIFFERENT! |
| 4 | Wait 2-3 seconds | Counts converge: S1: 54, S2: 54, S3: 54 |
| 5 | Point out replication lag | "S3 was 3 messages behind for ~2 seconds" |
| 6 | Explain | "In eventual consistency, the client gets an instant response, but replicas catch up asynchronously" |

### Actions — Quorum Consistency

| Step | Action | What to Show |
|---|---|---|
| 1 | Switch to "Quorum" (W=2, R=2, N=3) | Click dropdown → Quorum |
| 2 | Alice sends "Test Quorum" | Message sends, waits for 2 ACKs |
| 3 | Show counts | S1: 55, S2: 55, S3: 54 — S3 slightly behind |
| 4 | Explain | "We only needed 2 out of 3 servers to ACK. S3 will catch up." |
| 5 | Kill S3 | Quorum writes still work! (W=2, only need S1 + S2) |
| 6 | Kill S2 | Quorum writes FAIL (can't reach W=2 with only S1) |
| 7 | Show error | "Write failed: quorum not met (1/2)" |

### Talking Points
- "Strong consistency = slowest but always consistent (like a bank transaction)"
- "Eventual consistency = fastest but temporarily inconsistent (like social media feeds)"
- "Quorum = balance of speed and consistency (configurable trade-off)"
- "This is the CAP theorem in action — you can't have all three: Consistency, Availability, Partition tolerance"

---

## Demo Scenario 7: Load Balancer (7 minutes)

### Setup
- Open DC Dashboard → **Load Balancer** tab
- All 3 servers active

### Actions — Round Robin

| Step | Action | What to Show |
|---|---|---|
| 1 | Algorithm: "Round Robin" | Dashboard shows RR mode |
| 2 | Click "Simulate Load" (sends 9 requests) | Watch per-server request counters |
| 3 | Show distribution | S1: 3 requests, S2: 3 requests, S3: 3 requests — even! |

### Actions — Server Failure

| Step | Action | What to Show |
|---|---|---|
| 1 | **Kill Server 2** | S2 goes down |
| 2 | LB detects health check failure | "S2 removed from pool (3 failed health checks)" |
| 3 | Click "Simulate Load" (sends 6 requests) | S1: 3, S3: 3 — only alive servers get traffic |
| 4 | **Revive Server 2** | S2 comes back |
| 5 | LB detects S2 is healthy again | "S2 added back to pool" |
| 6 | Click "Simulate Load" | S1: 2, S2: 2, S3: 2 — S2 is back in rotation |

### Actions — Algorithm Switch

| Step | Action | What to Show |
|---|---|---|
| 1 | Connect 5 extra clients to S1 (or simulate) | S1 has 5 connections, S2 has 1, S3 has 1 |
| 2 | Switch to "Least Connections" | Dashboard shows LC mode |
| 3 | Send a new request | Goes to S2 or S3 (fewer connections), NOT S1 |
| 4 | Show distribution | "Smart routing — new requests avoid the overloaded server" |

### Talking Points
- "Load balancer sits between the frontend and backend servers"
- "Round Robin is fair but dumb — it doesn't know if a server is overloaded"
- "Least Connections is smarter — new traffic goes to the least busy server"
- "Health checks run every 5 seconds — a failed server is removed within 15 seconds"
- "Sticky sessions ensure a user's WebSocket stays on the same server"

---

## Demo Scenario 8: Cascading Failure (5 minutes — Grand Finale)

> This is the money shot. One action triggers multiple DC topics in sequence.

### Actions

| Step | Time | Action | DC Topic Shown |
|---|---|---|---|
| 1 | 0:00 | Everything running normally, chat active | — |
| 2 | 0:10 | **Kill Server 3 (Coordinator)** | Fault Tolerance |
| 3 | 0:15 | Load Balancer removes S3 from pool | Load Balancer |
| 4 | 0:19 | S1 and S2 detect S3 is dead (heartbeat timeout) | Fault Tolerance |
| 5 | 0:20 | Election triggered (Bully: S2 sends ELECTION to S3, no response) | Bully/Ring |
| 6 | 0:22 | S2 declares self coordinator | Bully/Ring |
| 7 | 0:23 | S2 starts Berkeley clock sync as new coordinator | Clock Sync |
| 8 | 0:25 | S2 runs Berkeley algorithm on S1 and itself | Clock Sync |
| 9 | 0:26 | Message replication now only between S1 and S2 | RMI, Data Consistency |
| 10 | 0:30 | Alice sends a message — replicated only to S2 | Multithreading (replication pool) |
| 11 | 0:35 | All visible in dashboard: S3 🔴, S2 is coordinator, clocks synced, LB routing to S1+S2 | All Topics |
| 12 | 0:40 | **Revive S3** — syncs messages, re-registers RMI, sends heartbeat | Fault Recovery |
| 13 | 0:45 | S3 has higher ID → triggers election → becomes coordinator again | Bully/Ring |
| 14 | 0:50 | S3 runs Berkeley sync → all clocks re-synced | Clock Sync |
| 15 | 0:55 | LB adds S3 back to pool | Load Balancer |
| 16 | 1:00 | System fully recovered ✅ | All Topics |

### Talking Points
- "One server failure triggered a cascade of distributed computing events"
- "Fault tolerance detected the failure, election chose a new leader, clock sync adapted, load balancer re-routed traffic, data consistency maintained"
- "This is why distributed computing matters — a real distributed system must handle all of this automatically"
- "And all of this happened in under 60 seconds with zero data loss"

---

## Quick Reference: Dashboard Buttons

| Button | What It Does | Which Demo |
|---|---|---|
| Kill Server N | Stops a server's services | Fault Tolerance, LB, Election |
| Revive Server N | Restarts a server | Fault Recovery |
| Trigger Election | Forces a new election | Bully & Ring |
| Switch Algorithm | Bully ↔ Ring | Bully & Ring |
| Skew Clock | Adds offset to a server's clock | Clock Sync |
| Run Berkeley Sync | Triggers clock synchronization | Clock Sync |
| Switch Consistency | Strong / Eventual / Quorum | Data Consistency |
| Simulate Load | Sends burst of HTTP requests | Load Balancer |
| Switch LB Algorithm | RR / LC / Weighted | Load Balancer |

---

## Troubleshooting During Demo

| Problem | Solution |
|---|---|
| Container won't start | `docker-compose logs <service>` → check error |
| RMI connection refused | Verify RMI registry is up: `docker logs minicord-rmi-registry` |
| WebSocket won't connect | Check LB is running, check CORS settings |
| Election not triggering | Increase heartbeat timeout, check election monitor thread |
| Messages not replicating | Check RMI logs for RemoteException |
| Dashboard not updating | Verify admin WebSocket connection |
| Database error | `docker exec -it minicord-db-primary psql -U minicord` |
