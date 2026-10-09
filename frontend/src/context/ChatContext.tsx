import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { Server, Channel, Message, User } from '../types';
import { api } from '../services/api';
import { socket } from '../services/socket';
import { useAuth } from './AuthContext';

interface ChatContextType {
  servers: Server[];
  activeServer: Server | null;
  activeChannel: Channel | null;
  messages: Message[];
  members: User[];
  typingUsers: string[];
  isLoadingServers: boolean;
  isLoadingMessages: boolean;
  setActiveServer: (server: Server | null) => void;
  setActiveChannel: (channel: Channel | null) => void;
  sendMessage: (content: string) => Promise<void>;
  sendTyping: () => void;
  createServer: (name: string) => Promise<Server>;
  joinServer: (inviteCode: string) => Promise<Server>;
  createChannel: (name: string) => Promise<Channel>;
  refreshServers: () => Promise<void>;
  refreshMessages: () => Promise<void>;
}

const ChatContext = createContext<ChatContextType | undefined>(undefined);

export const ChatProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, token } = useAuth();
  const [servers, setServers] = useState<Server[]>([]);
  const [activeServer, setActiveServerState] = useState<Server | null>(null);
  const [activeChannel, setActiveChannelState] = useState<Channel | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [members, setMembers] = useState<User[]>([]);
  const [typingUsers, setTypingUsers] = useState<string[]>([]);
  const [isLoadingServers, setIsLoadingServers] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);

  // Fetch servers on auth
  const refreshServers = useCallback(async () => {
    if (!token) return;
    setIsLoadingServers(true);
    try {
      const data = await api.servers.list();
      setServers(data.servers || []);

      if (data.servers && data.servers.length > 0) {
        if (!activeServer || !data.servers.some((s) => s.id === activeServer.id)) {
          const firstServer = data.servers[0];
          setActiveServerState(firstServer);
          if (firstServer.channels && firstServer.channels.length > 0) {
            setActiveChannelState(firstServer.channels[0]);
          }
        }
      }
    } catch (err) {
      console.error('Failed to fetch servers:', err);
    } finally {
      setIsLoadingServers(false);
    }
  }, [token, activeServer]);

  useEffect(() => {
    if (token) {
      refreshServers();
    } else {
      setServers([]);
      setActiveServerState(null);
      setActiveChannelState(null);
      setMessages([]);
    }
  }, [token]);

  // When active server changes, pick its first channel
  const setActiveServer = (srv: Server | null) => {
    setActiveServerState(srv);
    if (srv && srv.channels && srv.channels.length > 0) {
      setActiveChannelState(srv.channels[0]);
    } else {
      setActiveChannelState(null);
    }
  };

  // When active channel changes, load messages and subscribe to socket room
  const setActiveChannel = (ch: Channel | null) => {
    if (activeChannel && activeChannel.id !== ch?.id) {
      socket.leaveChannel(activeChannel.id);
    }
    setActiveChannelState(ch);
  };

  const refreshMessages = useCallback(async () => {
    if (!activeChannel) {
      setMessages([]);
      return;
    }
    setIsLoadingMessages(true);
    try {
      const data = await api.messages.list(activeChannel.id, 50);
      // Sort messages causally by Lamport timestamp
      const sorted = (data.messages || []).sort((a, b) => {
        if (a.lamportTimestamp !== b.lamportTimestamp) {
          return a.lamportTimestamp - b.lamportTimestamp;
        }
        return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      });
      setMessages(sorted);
    } catch (err) {
      console.error('Failed to load messages:', err);
    } finally {
      setIsLoadingMessages(false);
    }
  }, [activeChannel]);

  useEffect(() => {
    if (activeChannel) {
      refreshMessages();
      socket.joinChannel(activeChannel.id);
    } else {
      setMessages([]);
    }
  }, [activeChannel, refreshMessages]);

  // Socket listener for new messages & typing
  useEffect(() => {
    const unsubMsg = socket.onMessage((newMsg: Message) => {
      if (activeChannel && newMsg.channelId === activeChannel.id) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === newMsg.id)) return prev;
          const updated = [...prev, newMsg];
          // Keep causally ordered
          return updated.sort((a, b) => {
            if (a.lamportTimestamp !== b.lamportTimestamp) {
              return a.lamportTimestamp - b.lamportTimestamp;
            }
            return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
          });
        });
      }
    });

    const unsubTyping = socket.onTyping((data) => {
      if (activeChannel && data.channelId === activeChannel.id && data.userId !== user?.id) {
        setTypingUsers((prev) => Array.from(new Set([...prev, data.username])));
        setTimeout(() => {
          setTypingUsers((prev) => prev.filter((u) => u !== data.username));
        }, 3000);
      }
    });

    return () => {
      unsubMsg();
      unsubTyping();
    };
  }, [activeChannel, user?.id]);

  const sendMessage = async (content: string) => {
    if (!activeChannel || !content.trim()) return;
    const msg = await api.messages.send(activeChannel.id, content.trim());
    setMessages((prev) => {
      if (prev.some((m) => m.id === msg.id)) return prev;
      return [...prev, msg].sort((a, b) => {
        if (a.lamportTimestamp !== b.lamportTimestamp) {
          return a.lamportTimestamp - b.lamportTimestamp;
        }
        return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      });
    });
  };

  const sendTyping = () => {
    if (activeChannel) {
      socket.sendTyping(activeChannel.id);
    }
  };

  const createServer = async (name: string): Promise<Server> => {
    const srv = await api.servers.create(name);
    await refreshServers();
    setActiveServerState(srv);
    if (srv.channels && srv.channels.length > 0) {
      setActiveChannelState(srv.channels[0]);
    }
    return srv;
  };

  const joinServer = async (inviteCode: string): Promise<Server> => {
    const srv = await api.servers.join(inviteCode);
    await refreshServers();
    setActiveServerState(srv);
    if (srv.channels && srv.channels.length > 0) {
      setActiveChannelState(srv.channels[0]);
    }
    return srv;
  };

  const createChannel = async (name: string): Promise<Channel> => {
    if (!activeServer) throw new Error('No active server selected');
    const ch = await api.servers.createChannel(activeServer.id, name);
    await refreshServers();
    setActiveChannelState(ch);
    return ch;
  };

  return (
    <ChatContext.Provider
      value={{
        servers,
        activeServer,
        activeChannel,
        messages,
        members,
        typingUsers,
        isLoadingServers,
        isLoadingMessages,
        setActiveServer,
        setActiveChannel,
        sendMessage,
        sendTyping,
        createServer,
        joinServer,
        createChannel,
        refreshServers,
        refreshMessages,
      }}
    >
      {children}
    </ChatContext.Provider>
  );
};

export const useChat = () => {
  const ctx = useContext(ChatContext);
  if (!ctx) throw new Error('useChat must be used within ChatProvider');
  return ctx;
};
