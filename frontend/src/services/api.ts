import {
  User,
  Server,
  Channel,
  Message,
  NodeStatus,
  ClockInfo,
  ElectionRecord,
  LBStats,
  ThreadDump,
  RmiLog,
  ConsistencyStatus,
} from '../types';

const BASE_URL = ''; // Relative URL works with Vite proxy (/api)

function getAuthToken(): string | null {
  return localStorage.getItem('minicord_token');
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getAuthToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers,
  });

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const errorMsg = data?.error?.message || data?.message || response.statusText;
    throw new Error(errorMsg || `Request failed with status ${response.status}`);
  }

  // Handle ApiResponse<T> wrapper
  if (data && typeof data === 'object' && 'data' in data) {
    return data.data as T;
  }
  return data as T;
}

export const api = {
  // Auth
  auth: {
    register: (username: string, email: string, password: string) =>
      request<{ id: string; username: string; email: string; token: string }>(
        '/api/auth/register',
        { method: 'POST', body: JSON.stringify({ username, email, password }) }
      ),
    login: (email: string, password: string) =>
      request<{ id: string; username: string; token: string }>(
        '/api/auth/login',
        { method: 'POST', body: JSON.stringify({ email, password }) }
      ),
    logout: () =>
      request<{ message: string }>('/api/auth/logout', { method: 'POST' }),
  },

  // Servers & Channels
  servers: {
    list: () => request<{ servers: Server[] }>('/api/servers'),
    create: (name: string) =>
      request<Server>('/api/servers', {
        method: 'POST',
        body: JSON.stringify({ name }),
      }),
    join: (inviteCode: string) =>
      request<Server>('/api/servers/join', {
        method: 'POST',
        body: JSON.stringify({ inviteCode }),
      }),
    delete: (serverId: string) =>
      request<{ message: string }>(`/api/servers/${serverId}`, {
        method: 'DELETE',
      }),
    getChannels: (serverId: string) =>
      request<{ channels: Channel[] }>(`/api/servers/${serverId}/channels`),
    createChannel: (serverId: string, name: string) =>
      request<Channel>(`/api/servers/${serverId}/channels`, {
        method: 'POST',
        body: JSON.stringify({ name }),
      }),
    deleteChannel: (channelId: string) =>
      request<{ message: string }>(`/api/channels/${channelId}`, {
        method: 'DELETE',
      }),
  },

  // Messages
  messages: {
    list: (channelId: string, limit = 50, before?: string) => {
      let query = `?limit=${limit}`;
      if (before) query += `&before=${encodeURIComponent(before)}`;
      return request<{ messages: Message[]; hasMore: boolean }>(
        `/api/channels/${channelId}/messages${query}`
      );
    },
    send: (channelId: string, content: string) =>
      request<Message>(`/api/channels/${channelId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ content }),
      }),
  },

  // DC Admin Dashboard Endpoints (7 topics)
  admin: {
    // Topic 4: Fault Tolerance Nodes
    getNodes: () =>
      request<{ nodes: NodeStatus[]; coordinator: number }>('/api/admin/nodes'),
    killNode: (nodeId: number) =>
      request<{ message: string }>(`/api/admin/node/${nodeId}/kill`, {
        method: 'POST',
      }),
    reviveNode: (nodeId: number) =>
      request<{ message: string }>(`/api/admin/node/${nodeId}/revive`, {
        method: 'POST',
      }),

    // Topic 3: Clock Synchronization
    getClocks: () =>
      request<{ clocks: ClockInfo[]; lastSyncTime: string; syncAlgorithm: string }>(
        '/api/admin/clocks'
      ),
    setClockSkew: (serverId: number, offsetMs: number) =>
      request<{ serverId: number; offsetMs: number }>('/api/admin/clock/skew', {
        method: 'POST',
        body: JSON.stringify({ serverId, offsetMs }),
      }),
    syncClocks: () =>
      request<{ adjustments: { serverId: number; offsetMs: number }[]; averageTime: string }>(
        '/api/admin/clock/sync',
        { method: 'POST' }
      ),

    // Topic 6: Bully & Ring Election
    getElectionHistory: () =>
      request<{ elections: ElectionRecord[] }>('/api/admin/election/history'),
    triggerElection: (algorithm?: 'BULLY' | 'RING') =>
      request<ElectionRecord>('/api/admin/election/trigger', {
        method: 'POST',
        body: JSON.stringify({ algorithm: algorithm || 'BULLY' }),
      }),

    // Topic 5: Data Consistency
    getConsistencyStatus: () =>
      request<ConsistencyStatus>('/api/admin/consistency/status'),
    setConsistencyMode: (mode: 'STRICT_QUORUM' | 'EVENTUAL' | 'READ_YOUR_WRITES') =>
      request<{ mode: string }>('/api/admin/consistency/mode', {
        method: 'POST',
        body: JSON.stringify({ mode }),
      }),

    // Topic 2: Thread Monitoring
    getThreads: (serverId: number = 1) =>
      request<ThreadDump>(`/api/admin/threads/${serverId}`),

    // Topic 1: Java RMI Call Logs
    getRmiLogs: (limit = 50, method?: string) => {
      let query = `?limit=${limit}`;
      if (method) query += `&method=${encodeURIComponent(method)}`;
      return request<{ logs: RmiLog[] }>(`/api/admin/rmi/logs${query}`);
    },

    // Topic 7: Load Balancer
    getLbStats: () =>
      request<LBStats>('/api/admin/lb/stats'),
    setLbAlgorithm: (algorithm: 'ROUND_ROBIN' | 'LEAST_CONNECTIONS' | 'WEIGHTED_ROUND_ROBIN') =>
      request<{ algorithm: string }>('/api/admin/lb/algorithm', {
        method: 'POST',
        body: JSON.stringify({ algorithm }),
      }),
  },
};
