import React, { useState, useRef, useEffect } from 'react';
import { useChat } from '../../context/ChatContext';
import { MessageItem } from './MessageItem';

export const ChatArea: React.FC = () => {
  const { activeChannel, activeServer, messages, sendMessage, sendTyping, typingUsers, isLoadingMessages } = useChat();
  const [inputText, setInputText] = useState('');
  const [isSending, setIsSending] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    } else {
      sendTyping();
    }
  };

  const handleSend = async () => {
    if (!inputText.trim() || isSending) return;
    setIsSending(true);
    const content = inputText;
    setInputText('');
    try {
      await sendMessage(content);
    } catch (err) {
      console.error('Failed to send message:', err);
      setInputText(content);
    } finally {
      setIsSending(false);
    }
  };

  if (!activeServer) {
    return (
      <div className="main-content">
        <div className="empty-state">
          <div className="icon">💬</div>
          <h3>Welcome to MiniCord</h3>
          <p>Select a server from the sidebar or create a new one to start chatting.</p>
        </div>
      </div>
    );
  }

  if (!activeChannel) {
    return (
      <div className="main-content">
        <div className="empty-state">
          <div className="icon">#️⃣</div>
          <h3>No Channel Selected</h3>
          <p>Please select or create a channel from the sidebar.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="main-content">
      {/* Chat header */}
      <div className="chat-header">
        <span className="hash">#</span>
        <span className="channel-name">{activeChannel.name}</span>
        <div className="divider" />
        <span className="topic">
          Real-time channel on {activeServer.name} • Causal ordering with Lamport & Vector clocks
        </span>
      </div>

      {/* Messages list */}
      <div className="messages-container">
        {isLoadingMessages && messages.length === 0 ? (
          <div className="empty-state">
            <p>Loading messages...</p>
          </div>
        ) : messages.length === 0 ? (
          <div className="empty-state">
            <div className="icon">👋</div>
            <h3>This is the beginning of #{activeChannel.name}</h3>
            <p>Send a message to start the conversation across the distributed cluster!</p>
          </div>
        ) : (
          messages.map((msg) => <MessageItem key={msg.id} message={msg} />)
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Typing indicator */}
      <div className="typing-indicator">
        {typingUsers.length > 0 && (
          <span>
            <strong>{typingUsers.join(', ')}</strong> {typingUsers.length === 1 ? 'is' : 'are'} typing...
          </span>
        )}
      </div>

      {/* Input bar */}
      <div className="message-input-container">
        <div className="message-input-wrapper">
          <input
            type="text"
            className="message-input"
            placeholder={`Message #${activeChannel.name} (Press Enter to send)`}
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            onKeyDown={handleKeyDown}
            disabled={isSending}
            autoFocus
          />
          <button
            className="send-btn"
            onClick={handleSend}
            disabled={!inputText.trim() || isSending}
          >
            Send
          </button>
        </div>
      </div>
    </div>
  );
};
