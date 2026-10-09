import { Message } from '../types';

type MessageHandler = (message: Message) => void;
type TypingHandler = (data: { userId: string; username: string; channelId: string }) => void;
type PresenceHandler = (data: { userId: string; username: string; status: string }) => void;
type DashboardEventHandler = (eventType: string, data: any) => void;

class SocketService {
  private ws: WebSocket | null = null;
  private messageListeners: Set<MessageHandler> = new Set();
  private typingListeners: Set<TypingHandler> = new Set();
  private presenceListeners: Set<PresenceHandler> = new Set();
  private dashboardListeners: Set<DashboardEventHandler> = new Set();
  private currentChannelId: string | null = null;
  private reconnectTimeout: any = null;
  private isExplicitlyClosed = false;

  public connect(token?: string) {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    this.isExplicitlyClosed = false;
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    let url = `${protocol}//${host}/ws`;

    const authToken = token || localStorage.getItem('minicord_token');
    if (authToken) {
      url += `?token=${encodeURIComponent(authToken)}`;
    }

    try {
      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        console.log('[WebSocket] Connected');
        if (this.currentChannelId) {
          this.joinChannel(this.currentChannelId);
        }
      };

      this.ws.onmessage = (event) => {
        try {
          const payload = JSON.parse(event.data);
          const { type, data } = payload;

          if (type === 'new_message' && data) {
            this.messageListeners.forEach((fn) => fn(data));
          } else if (type === 'user_typing' && data) {
            this.typingListeners.forEach((fn) => fn(data));
          } else if (type === 'presence_update' && data) {
            this.presenceListeners.forEach((fn) => fn(data));
          } else if (type === 'dashboard_event' && data) {
            this.dashboardListeners.forEach((fn) => fn(data.eventType, data.eventData));
          }
        } catch (err) {
          console.error('[WebSocket] Failed to parse message', err);
        }
      };

      this.ws.onclose = () => {
        console.log('[WebSocket] Disconnected');
        if (!this.isExplicitlyClosed) {
          clearTimeout(this.reconnectTimeout);
          this.reconnectTimeout = setTimeout(() => this.connect(token), 3000);
        }
      };

      this.ws.onerror = (err) => {
        console.warn('[WebSocket] Error:', err);
      };
    } catch (e) {
      console.error('[WebSocket] Connection attempt failed:', e);
    }
  }

  public disconnect() {
    this.isExplicitlyClosed = true;
    clearTimeout(this.reconnectTimeout);
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
  }

  public joinChannel(channelId: string) {
    this.currentChannelId = channelId;
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        type: 'join_channel',
        data: { channelId },
      }));
    }
  }

  public leaveChannel(channelId: string) {
    if (this.currentChannelId === channelId) {
      this.currentChannelId = null;
    }
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        type: 'leave_channel',
        data: { channelId },
      }));
    }
  }

  public sendTyping(channelId: string) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        type: 'typing',
        data: { channelId },
      }));
    }
  }

  public onMessage(handler: MessageHandler) {
    this.messageListeners.add(handler);
    return () => this.messageListeners.delete(handler);
  }

  public onTyping(handler: TypingHandler) {
    this.typingListeners.add(handler);
    return () => this.typingListeners.delete(handler);
  }

  public onPresence(handler: PresenceHandler) {
    this.presenceListeners.add(handler);
    return () => this.presenceListeners.delete(handler);
  }

  public onDashboardEvent(handler: DashboardEventHandler) {
    this.dashboardListeners.add(handler);
    return () => this.dashboardListeners.delete(handler);
  }
}

export const socket = new SocketService();
