import React, { useState, useEffect, useCallback } from 'react';
import { api } from '../../services/api';
import { socket } from '../../services/socket';
import {
  NodeStatus,
  ClockInfo,
  ElectionRecord,
  LBStats,
  ThreadDump,
  RmiLog,
  ConsistencyStatus,
} from '../../types';

export const AdminDashboard: React.FC = () => {
  // Topic 4: Fault Tolerance
  const [nodes, setNodes] = useState<NodeStatus[]>([]);
  const [coordinatorId, setCoordinatorId] = useState<number>(3);

  // Topic 7: Load Balancer
  const [lbStats, setLbStats] = useState<LBStats | null>(null);

  // Topic 3: Clock Synchronization
  const [clocks, setClocks] = useState<ClockInfo[]>([]);
  const [skewNode, setSkewNode] = useState<number>(2);
  const [skewOffset, setSkewOffset] = useState<number>(3000);
  const [lastSyncResult, setLastSyncResult] = useState<string>('');

  // Topic 6: Bully & Ring Election
  const [elections, setElections] = useState<ElectionRecord[]>([]);
  const [electionLoading, setElectionLoading] = useState(false);

  // Topic 5: Data Consistency
  const [consistency, setConsistency] = useState<ConsistencyStatus | null>(null);

  // Topic 2: Multithreading
  const [threadDump, setThreadDump] = useState<ThreadDump | null>(null);

  // Topic 1: Java RMI Call Logs
  const [rmiLogs, setRmiLogs] = useState<RmiLog[]>([]);
  const [logFilter, setLogFilter] = useState<string>('');

  const [loading, setLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState<string>('');

  const showNotification = (msg: string) => {
    setActionMessage(msg);
    setTimeout(() => setActionMessage(''), 4000);
  };

  const loadData = useCallback(async () => {
    try {
      // Fetch in parallel
      const [nodesData, lbData, clocksData, electData, consData, threadsData, rmiData] =
        await Promise.allSettled([
          api.admin.getNodes(),
          api.admin.getLbStats(),
          api.admin.getClocks(),
          api.admin.getElectionHistory(),
          api.admin.getConsistencyStatus(),
          api.admin.getThreads(1),
          api.admin.getRmiLogs(25, logFilter || undefined),
        ]);

      if (nodesData.status === 'fulfilled') {
        setNodes(nodesData.value.nodes || []);
        if (nodesData.value.coordinator) setCoordinatorId(nodesData.value.coordinator);
      }
      if (lbData.status === 'fulfilled') {
        setLbStats(lbData.value);
      }
      if (clocksData.status === 'fulfilled') {
        setClocks(clocksData.value.clocks || []);
      }
      if (electData.status === 'fulfilled') {
        setElections(electData.value.elections || []);
      }
      if (consData.status === 'fulfilled') {
        setConsistency(consData.value);
      }
      if (threadsData.status === 'fulfilled') {
        setThreadDump(threadsData.value);
      }
      if (rmiData.status === 'fulfilled') {
        setRmiLogs(rmiData.value.logs || []);
      }
    } catch (e) {
      console.error('Error fetching admin data:', e);
    }
  }, [logFilter]);

  // Initial fetch and 4-second polling
  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 4000);
    return () => clearInterval(interval);
  }, [loadData]);

  // WebSocket live updates for dashboard events
  useEffect(() => {
    const unsub = socket.onDashboardEvent((eventType, data) => {
      if (eventType === 'election_complete') {
        showNotification(`⚡ Election Complete: Node ${data.newCoordinator} elected via ${data.algorithm} algorithm (${data.durationMs}ms)`);
        loadData();
      } else if (eventType === 'node_status_update') {
        showNotification(`📡 Node ${data.nodeId} status changed to ${data.status}`);
        loadData();
      }
    });
    return () => {
      unsub();
    };
  }, [loadData]);

  // Topic 4 actions: Kill / Revive
  const handleKillNode = async (id: number) => {
    try {
      await api.admin.killNode(id);
      showNotification(`Simulated crash triggered on Node ${id}`);
      loadData();
    } catch (err: any) {
      alert(`Error killing node: ${err.message}`);
    }
  };

  const handleReviveNode = async (id: number) => {
    try {
      await api.admin.reviveNode(id);
      showNotification(`Node ${id} revived and state resynchronized`);
      loadData();
    } catch (err: any) {
      alert(`Error reviving node: ${err.message}`);
    }
  };

  // Topic 6 action: Trigger Election
  const handleTriggerElection = async (algo: 'BULLY' | 'RING') => {
    setElectionLoading(true);
    try {
      const record = await api.admin.triggerElection(algo);
      showNotification(`Election triggered: Node ${record.newCoordinator} elected using ${record.algorithm}!`);
      loadData();
    } catch (err: any) {
      alert(`Election error: ${err.message}`);
    } finally {
      setElectionLoading(false);
    }
  };

  // Topic 3 actions: Clock skew & sync
  const handleInjectSkew = async () => {
    try {
      await api.admin.setClockSkew(skewNode, skewOffset);
      showNotification(`Injected offset of ${skewOffset}ms onto Node ${skewNode}`);
      loadData();
    } catch (err: any) {
      alert(`Skew injection error: ${err.message}`);
    }
  };

  const handleSyncClocks = async () => {
    try {
      const res = await api.admin.syncClocks();
      setLastSyncResult(`Berkeley Sync completed: Avg Time = ${res.averageTime}. Adjustments applied.`);
      showNotification(`Berkeley Clock Sync complete!`);
      loadData();
    } catch (err: any) {
      alert(`Sync error: ${err.message}`);
    }
  };

  // Topic 5 action: Set Consistency Mode
  const handleSetConsistency = async (mode: 'STRICT_QUORUM' | 'EVENTUAL' | 'READ_YOUR_WRITES') => {
    try {
      await api.admin.setConsistencyMode(mode);
      showNotification(`Consistency mode updated to ${mode}`);
      loadData();
    } catch (err: any) {
      alert(`Consistency change error: ${err.message}`);
    }
  };

  // Topic 7 action: Set LB Algorithm
  const handleSetLbAlgorithm = async (algo: 'ROUND_ROBIN' | 'LEAST_CONNECTIONS' | 'WEIGHTED_ROUND_ROBIN') => {
    try {
      await api.admin.setLbAlgorithm(algo);
      showNotification(`Load Balancer algorithm changed to ${algo}`);
      loadData();
    } catch (err: any) {
      alert(`LB switch error: ${err.message}`);
    }
  };

  return (
    <div className="admin-dashboard">
      {/* Top Banner / Actions Notification */}
      <div className="admin-header">
        <div>
          <h2>Distributed Computing Lab Panel</h2>
          <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginTop: '4px' }}>
            Live cluster simulation of 7 core Distributed Computing topics
          </div>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          {actionMessage && (
            <div
              style={{
                fontSize: '12px',
                padding: '4px 12px',
                background: 'rgba(99, 102, 241, 0.2)',
                color: 'var(--accent-primary-hover)',
                borderRadius: '4px',
                border: '1px solid var(--border-active)',
              }}
            >
              {actionMessage}
            </div>
          )}
          <button className="btn btn-primary btn-sm" onClick={loadData}>
            🔄 Refresh
          </button>
        </div>
      </div>

      <div className="admin-grid">
        {/* =========================================================
            TOPIC 7: LOAD BALANCER
           ========================================================= */}
        <div className="admin-card">
          <h3>
            <span className="icon">⚖️</span> Topic 7: Load Balancer
          </h3>
          <div style={{ marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Algorithm:</span>
            <select
              className="admin-select"
              value={lbStats?.algorithm || 'ROUND_ROBIN'}
              onChange={(e) => handleSetLbAlgorithm(e.target.value as any)}
            >
              <option value="ROUND_ROBIN">Round Robin</option>
              <option value="LEAST_CONNECTIONS">Least Connections</option>
              <option value="WEIGHTED_ROUND_ROBIN">Weighted Round Robin</option>
            </select>
          </div>

          <table className="admin-table">
            <thead>
              <tr>
                <th>Backend</th>
                <th>Status</th>
                <th>Active Conn</th>
                <th>Total Req</th>
                <th>Avg Latency</th>
              </tr>
            </thead>
            <tbody>
              {lbStats?.servers && lbStats.servers.length > 0 ? (
                lbStats.servers.map((s) => (
                  <tr key={s.id}>
                    <td><strong>Server {s.id}</strong></td>
                    <td>
                      <span className={`badge ${s.status === 'HEALTHY' ? 'alive' : 'dead'}`}>
                        {s.status}
                      </span>
                    </td>
                    <td>{s.activeConnections}</td>
                    <td>{s.totalRequests}</td>
                    <td>{s.avgResponseTimeMs} ms</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                    No load balancer stats available yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* =========================================================
            TOPIC 4: FAULT TOLERANCE & HEARTBEAT
           ========================================================= */}
        <div className="admin-card">
          <h3>
            <span className="icon">🛡️</span> Topic 4: Fault Tolerance & Failure Simulation
          </h3>
          <div style={{ fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '12px' }}>
            Simulate node failure / crash or revive node to observe recovery:
          </div>

          <div className="topology-nodes">
            {(nodes.length > 0 ? nodes : [
              { id: 1, url: 'http://localhost:8081', status: 'ALIVE', isCoordinator: coordinatorId === 1, activeConnections: 0, lastHeartbeat: '' },
              { id: 2, url: 'http://localhost:8082', status: 'ALIVE', isCoordinator: coordinatorId === 2, activeConnections: 0, lastHeartbeat: '' },
              { id: 3, url: 'http://localhost:8083', status: 'ALIVE', isCoordinator: coordinatorId === 3, activeConnections: 0, lastHeartbeat: '' },
            ] as NodeStatus[]).map((node) => {
              const isDead = node.status === 'DEAD';
              const isCoord = node.isCoordinator || node.id === coordinatorId;

              return (
                <div key={node.id} className={`topology-node ${isDead ? 'dead' : 'alive'}`}>
                  <div className="node-id">
                    Node {node.id} {isCoord ? '👑' : ''}
                  </div>
                  <div className="node-role">
                    <span className={`badge ${isDead ? 'dead' : isCoord ? 'coordinator' : 'follower'}`}>
                      {isDead ? 'CRASHED' : isCoord ? 'COORDINATOR' : 'FOLLOWER'}
                    </span>
                  </div>
                  <div style={{ fontSize: '11px', color: 'var(--text-muted)', margin: '8px 0' }}>
                    Active Conn: {node.activeConnections || 0}
                  </div>
                  <div className="btn-group" style={{ justifyContent: 'center' }}>
                    {isDead ? (
                      <button className="btn btn-success btn-sm" onClick={() => handleReviveNode(node.id)}>
                        Revive
                      </button>
                    ) : (
                      <button className="btn btn-danger btn-sm" onClick={() => handleKillNode(node.id)}>
                        Kill Node
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* =========================================================
            TOPIC 6: BULLY & RING LEADER ELECTION
           ========================================================= */}
        <div className="admin-card">
          <h3>
            <span className="icon">👑</span> Topic 6: Bully & Ring Leader Election
          </h3>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '14px' }}>
            <div style={{ fontSize: '13px' }}>
              Current Coordinator: <span className="badge coordinator">Server {coordinatorId} 👑</span>
            </div>
            <div className="btn-group">
              <button
                className="btn btn-primary btn-sm"
                onClick={() => handleTriggerElection('BULLY')}
                disabled={electionLoading}
              >
                Trigger Bully
              </button>
              <button
                className="btn btn-warning btn-sm"
                onClick={() => handleTriggerElection('RING')}
                disabled={electionLoading}
              >
                Trigger Ring
              </button>
            </div>
          </div>

          <div style={{ fontSize: '11px', color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '6px' }}>
            Recent Elections:
          </div>
          <table className="admin-table">
            <thead>
              <tr>
                <th>Algo</th>
                <th>Initiator</th>
                <th>Old ➔ New</th>
                <th>Duration</th>
              </tr>
            </thead>
            <tbody>
              {elections.slice(0, 4).map((e) => (
                <tr key={e.id}>
                  <td><strong>{e.algorithm}</strong></td>
                  <td>Node {e.initiatedBy}</td>
                  <td>S{e.oldCoordinator} ➔ S{e.newCoordinator}</td>
                  <td>{e.durationMs}ms</td>
                </tr>
              ))}
              {elections.length === 0 && (
                <tr>
                  <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                    No election events yet. Trigger one above!
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* =========================================================
            TOPIC 3: CLOCK SYNCHRONIZATION (BERKELEY, LAMPORT, VECTOR)
           ========================================================= */}
        <div className="admin-card">
          <h3>
            <span className="icon">⏱️</span> Topic 3: Clock Synchronization (Berkeley / Lamport)
          </h3>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center', marginBottom: '12px' }}>
            <select
              className="admin-select"
              value={skewNode}
              onChange={(e) => setSkewNode(Number(e.target.value))}
            >
              <option value={1}>Node 1</option>
              <option value={2}>Node 2</option>
              <option value={3}>Node 3</option>
            </select>
            <input
              type="number"
              style={{
                width: '90px',
                padding: '6px 8px',
                background: 'var(--bg-input)',
                border: '1px solid var(--border-color)',
                borderRadius: '4px',
                color: 'white',
                fontSize: '12px',
              }}
              value={skewOffset}
              onChange={(e) => setSkewOffset(Number(e.target.value))}
              placeholder="Offset (ms)"
            />
            <button className="btn btn-danger btn-sm" onClick={handleInjectSkew}>
              Inject Skew
            </button>
            <button className="btn btn-success btn-sm" onClick={handleSyncClocks}>
              Berkeley Sync
            </button>
          </div>

          {lastSyncResult && (
            <div style={{ fontSize: '11px', color: 'var(--accent-success)', marginBottom: '8px' }}>
              ✓ {lastSyncResult}
            </div>
          )}

          <table className="admin-table">
            <thead>
              <tr>
                <th>Node</th>
                <th>Lamport</th>
                <th>Vector [S1,S2,S3]</th>
                <th>Skew Offset</th>
              </tr>
            </thead>
            <tbody>
              {clocks.map((c) => (
                <tr key={c.serverId}>
                  <td><strong>Node {c.serverId}</strong></td>
                  <td>{c.lamportClock}</td>
                  <td>[{Array.isArray(c.vectorClock) ? c.vectorClock.join(', ') : '0, 0, 0'}]</td>
                  <td style={{ color: c.clockOffset !== 0 ? 'var(--accent-danger)' : 'var(--accent-success)' }}>
                    {c.clockOffset > 0 ? `+${c.clockOffset}` : c.clockOffset} ms
                  </td>
                </tr>
              ))}
              {clocks.length === 0 && (
                <tr>
                  <td colSpan={4} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>
                    Loading clock states...
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {/* =========================================================
            TOPIC 5: DATA CONSISTENCY & REPLICATION
           ========================================================= */}
        <div className="admin-card">
          <h3>
            <span className="icon">🔄</span> Topic 5: Data Consistency
          </h3>
          <div style={{ marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>Consistency Mode:</span>
            <select
              className="admin-select"
              value={consistency?.mode || 'STRICT_QUORUM'}
              onChange={(e) => handleSetConsistency(e.target.value as any)}
            >
              <option value="STRICT_QUORUM">Strict Quorum (W+R &gt; N)</option>
              <option value="EVENTUAL">Eventual Consistency</option>
              <option value="READ_YOUR_WRITES">Read-Your-Writes</option>
            </select>
          </div>

          <div className="stat-row">
            <div className="stat-item">
              <div className="stat-value">{consistency?.writeQuorum ?? 2}</div>
              <div className="stat-label">Write Quorum (W)</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">{consistency?.readQuorum ?? 2}</div>
              <div className="stat-label">Read Quorum (R)</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">{consistency?.activeReplicas ?? 3}</div>
              <div className="stat-label">Total Replicas (N)</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">{consistency?.replicationLagMs ?? 0}ms</div>
              <div className="stat-label">Lag</div>
            </div>
          </div>
        </div>

        {/* =========================================================
            TOPIC 2: MULTITHREADING (THREAD POOL MONITOR)
           ========================================================= */}
        <div className="admin-card">
          <h3>
            <span className="icon">🧵</span> Topic 2: Multithreading & Thread Pool
          </h3>
          <div className="stat-row">
            <div className="stat-item">
              <div className="stat-value">{threadDump?.activeThreads ?? 4}</div>
              <div className="stat-label">Active Threads</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">{threadDump?.queueSize ?? 0}</div>
              <div className="stat-label">Queue Size</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">{threadDump?.completedTasks ?? 12}</div>
              <div className="stat-label">Tasks Done</div>
            </div>
            <div className="stat-item">
              <div className="stat-value">{threadDump?.maxPoolSize ?? 16}</div>
              <div className="stat-label">Max Pool Size</div>
            </div>
          </div>
          <div style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
            ReplicationThreadPool and MessageProcessorThread actively processing concurrent inbound messages and cross-node broadcasts.
          </div>
        </div>

        {/* =========================================================
            TOPIC 1: JAVA RMI / RPC LOGS
           ========================================================= */}
        <div className="admin-card" style={{ gridColumn: '1 / -1' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
            <h3>
              <span className="icon">🔌</span> Topic 1: Java RMI / RPC Logs
            </h3>
            <div style={{ display: 'flex', gap: '8px' }}>
              <select
                className="admin-select"
                value={logFilter}
                onChange={(e) => setLogFilter(e.target.value)}
              >
                <option value="">All RPC Methods</option>
                <option value="heartbeat">heartbeat</option>
                <option value="replicateMessage">replicateMessage</option>
                <option value="elect">elect</option>
                <option value="berkeleySync">berkeleySync</option>
              </select>
            </div>
          </div>

          <div className="log-list">
            {rmiLogs.length > 0 ? (
              rmiLogs.map((log) => {
                const time = new Date(log.timestamp).toLocaleTimeString();
                return (
                  <div key={log.id} className={`log-entry ${log.success ? 'success' : 'failed'}`}>
                    <span className="log-time">{time}</span>
                    <span className="log-route">S{log.callerServerId} ➔ S{log.receiverServerId}</span>
                    <span className="log-method">{log.methodName}()</span>
                    <span className="log-status">{log.executionTimeMs}ms • {log.success ? 'OK' : 'FAIL'}</span>
                    {log.errorMessage && <span style={{ color: 'var(--accent-danger)' }}>({log.errorMessage})</span>}
                  </div>
                );
              })
            ) : (
              <div style={{ padding: '16px', textAlign: 'center', color: 'var(--text-muted)', fontSize: '13px' }}>
                No RMI logs recorded yet. RMI heartbeat and replication calls will appear here.
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
