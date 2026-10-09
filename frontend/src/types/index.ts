export interface User {
  id: string;
  username: string;
  email?: string;
  avatarUrl?: string;
  status?: 'online' | 'offline' | 'idle';
}

export interface Channel {
  id: string;
  serverId: string;
  name: string;
  createdAt?: string;
}

export interface Server {
  id: string;
  name: string;
  ownerId: string;
  inviteCode?: string;
  channels: Channel[];
}

export interface MessageAuthor {
  id: string;
  username: string;
  avatarUrl?: string | null;
}

export interface Message {
  id: string;
  channelId: string;
  author: MessageAuthor;
  content: string;
  lamportTimestamp: number;
  vectorClock: number[];
  serverOrigin: number;
  createdAt: string;
}

export interface NodeStatus {
  id: number;
  url: string;
  status: 'ALIVE' | 'DEAD' | 'RECOVERING';
  isCoordinator: boolean;
  activeConnections: number;
  lastHeartbeat: string;
}

export interface ClockInfo {
  serverId: number;
  lamportClock: number;
  vectorClock: number[];
  physicalTime: string;
  clockOffset: number;
}

export interface ClockAdjustment {
  serverId: number;
  offsetMs: number;
}

export interface ElectionRecord {
  id: string;
  algorithm: 'BULLY' | 'RING';
  initiatedBy: number;
  newCoordinator: number;
  oldCoordinator: number;
  durationMs: number;
  messagesExchanged: number;
  timestamp: string;
}

export interface LBServerStat {
  id: number;
  status: 'HEALTHY' | 'UNHEALTHY';
  activeConnections: number;
  totalRequests: number;
  requestsPerSecond: number;
  avgResponseTimeMs: number;
}

export interface LBStats {
  algorithm: 'ROUND_ROBIN' | 'LEAST_CONNECTIONS' | 'WEIGHTED_ROUND_ROBIN';
  servers: LBServerStat[];
  totalActiveConnections: number;
  totalRequestsPerSecond: number;
}

export interface ThreadDump {
  serverId: number;
  activeThreads: number;
  peakThreads: number;
  corePoolSize: number;
  maxPoolSize: number;
  queueSize: number;
  completedTasks: number;
}

export interface RmiLog {
  id: string;
  timestamp: string;
  callerServerId: number;
  receiverServerId: number;
  methodName: string;
  executionTimeMs: number;
  success: boolean;
  errorMessage?: string;
}

export interface ConsistencyStatus {
  mode: 'STRICT_QUORUM' | 'EVENTUAL' | 'READ_YOUR_WRITES';
  writeQuorum: number;
  readQuorum: number;
  activeReplicas: number;
  pendingSyncs: number;
  replicationLagMs: number;
}
