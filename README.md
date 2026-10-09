# MiniCord (Mini Discord) — Distributed Computing Project

A full-scale distributed chat application built for Distributed Computing (DC) lab, containerized using Docker, with a React frontend and a multi-node Java backend cluster demonstrating **7 core Distributed Computing topics**.

---

## 🚀 The 7 Distributed Computing Topics Implemented

| # | DC Topic | Implementation in MiniCord | How to Demo & Verify |
|---|---|---|---|
| **1** | **Java RMI / RPC** | RMI Registry running in dedicated container (`minicord-rmi-registry:1099`). Remote interfaces for replication (`MessageReplicationService`), presence (`PresenceService`), coordinator election (`ElectionService`), and clock sync (`ClockSyncService`). | View real-time RPC call log table in DC Admin Dashboard showing caller, receiver, method, latency, and status. |
| **2** | **Multithreading** | Asynchronous execution pools (`ReplicationThreadPool`, `MessageProcessorThread`), concurrent data structures (`ConcurrentHashMap`, `LinkedBlockingQueue`), atomic variables. | View Thread Monitor in DC Admin Dashboard showing live pool statistics (active threads, tasks completed, queue size). |
| **3** | **Clock Synchronization** | Logical Clocks (Lamport timestamps & Vector Clocks per message) + Physical Clock Synchronization via **Berkeley Algorithm**. | Inspect Lamport `L: X` and Vector `V: [S1,S2,S3]` chips on chat messages. In DC Admin, inject artificial clock skew (+/- ms) into any node, then click "Berkeley Sync" to compute average time and apply adjustments. |
| **4** | **Fault Tolerance** | Periodic heartbeat health monitoring between nodes. Automatic failure detection and dynamic cluster re-balancing. State recovery upon node revival. | In DC Admin Dashboard, click **"Kill Node"** on any backend server to simulate a sudden crash. Watch the heartbeat failure detection, then click **"Revive"** to recover state. |
| **5** | **Data Consistency** | Replicated storage with 3 configurable consistency modes: **Strict Quorum** ($W + R > N$), **Eventual Consistency** (background gossip), and **Read-Your-Writes**. | Switch consistency modes from the DC Admin dropdown. View live write quorum ($W$), read quorum ($R$), replica count ($N$), and replication lag metrics. |
| **6** | **Leader Election** | Full implementation of both **Bully Algorithm** (highest ID wins) and **Ring Algorithm** (token circulation along virtual logical ring). | In DC Admin, observe current leader with golden crown 👑. Click **"Trigger Bully"** or **"Trigger Ring"** to simulate coordinator crash and watch election logs live. |
| **7** | **Load Balancer** | Custom reverse-proxy load balancer (`minicord-lb:8080`) supporting **Round Robin**, **Least Connections**, and **Weighted Round Robin**. Automatic health checks with failover away from dead nodes. | In DC Admin, switch load balancing algorithm dynamically and inspect live server traffic table (active connections, total requests handled, RPS, response latency). |

---

## 🛠️ Architecture & Containers

```
                       ┌───────────────────────┐
                       │   React Frontend      │
                       │   (Port 3000 / 80)    │
                       └───────────┬───────────┘
                                   │ HTTP & WS
                                   ▼
                       ┌───────────────────────┐
                       │   Load Balancer       │
                       │   (Port 8080)         │
                       └───────────┬───────────┘
               ┌───────────────────┼───────────────────┐
               ▼                   ▼                   ▼
       ┌───────────────┐   ┌───────────────┐   ┌───────────────┐
       │   Server 1    │   │   Server 2    │   │   Server 3    │
       │  (Port 9001)  │   │  (Port 9002)  │   │  (Port 9003)  │
       └───────┬───────┘   └───────┬───────┘   └───────┬───────┘
               │                   │                   │
               └───────────────────┼───────────────────┘
                                   │ Java RMI (Port 1099)
                                   ▼
                       ┌───────────────────────┐
                       │   RMI Registry        │
                       │   (Port 1099)         │
                       └───────────────────────┘
                                   │
                                   ▼
                       ┌───────────────────────┐
                       │  PostgreSQL Primary   │
                       │  & Replica (Port 5432)│
                       └───────────────────────┘
```

---

## 🐳 Docker Setup & Running

### Prerequisites
1. Install [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop/).
2. Start Docker Desktop and ensure the engine is running.

### 1-Click Launch
Run the included batch script or PowerShell script:
```powershell
.\start-docker.bat
# Or
.\start-docker.ps1
```

Or run Docker Compose directly in terminal:
```bash
docker compose up --build
```

### Accessing the Applications
- **MiniCord Chat & DC Dashboard**: [http://localhost:3000](http://localhost:3000)
- **Load Balancer Proxy**: [http://localhost:8080](http://localhost:8080)
- **Backend Node 1 Direct**: [http://localhost:9001](http://localhost:9001)
- **Backend Node 2 Direct**: [http://localhost:9002](http://localhost:9002)
- **Backend Node 3 Direct**: [http://localhost:9003](http://localhost:9003)
- **RMI Registry Direct**: `localhost:1099`

---

## 💻 Running Locally (Without Docker)

If you wish to run the components directly on your machine:

### 1. Start the React Frontend
```bash
cd frontend
npm install
npm run dev
```
The frontend starts on [http://localhost:3000](http://localhost:3000) and proxies `/api` and `/ws` to `localhost:8080`.

### 2. Build Java Backend and Load Balancer
```bash
# In backend/
.\gradlew.bat bootJar

# In loadbalancer/
.\gradlew.bat jar
```

---

## 🎯 Quick Demo Walkthrough

1. Open [http://localhost:3000](http://localhost:3000).
2. Click **"Demo: Alice"** to log in instantly.
3. Open an Incognito window, go to [http://localhost:3000](http://localhost:3000), and click **"Demo: Bob"**.
4. In Alice's window, create a Server (e.g. `DC Lab`). Copy the 8-character invite code from the header.
5. In Bob's window, click `🔗` on the left strip, paste the invite code, and join.
6. Create channels (e.g., `#general`, `#testing`).
7. Send messages back and forth:
   - Notice the **Lamport Timestamp** (`L: X`), **Vector Clock** (`V: [1, 0, 0]`), and **Origin Server** (`S1`) chips below every message!
8. Click the **⚡** icon in the leftmost server sidebar to open the **DC Admin Dashboard**:
   - **Kill a node** and watch the heartbeat switch to `CRASHED` and the load balancer redirect traffic.
   - Click **Trigger Bully** or **Trigger Ring** to elect a new coordinator in milliseconds.
   - Adjust clock offset and click **Berkeley Sync** to see physical clock adjustments applied.
   - Change Consistency Mode to see Quorum parameters ($W, R, N$) recalculate.
