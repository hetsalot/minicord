import React from 'react';
import { useChat } from '../../context/ChatContext';
import { useAuth } from '../../context/AuthContext';
import { Channel } from '../../types';

interface ChannelSidebarProps {
  onOpenCreateChannel: () => void;
}

export const ChannelSidebar: React.FC<ChannelSidebarProps> = ({ onOpenCreateChannel }) => {
  const { activeServer, activeChannel, setActiveChannel } = useChat();
  const { user, logout } = useAuth();

  const handleSelectChannel = (ch: Channel) => {
    setActiveChannel(ch);
  };

  return (
    <div className="channel-sidebar">
      {/* Server header */}
      <div className="channel-header" title={activeServer?.inviteCode ? `Invite Code: ${activeServer.inviteCode}` : ''}>
        <span>{activeServer ? activeServer.name : 'Select a Server'}</span>
        {activeServer?.inviteCode && (
          <span
            style={{
              fontSize: '10px',
              padding: '2px 6px',
              background: 'rgba(99, 102, 241, 0.2)',
              borderRadius: '4px',
              color: 'var(--accent-primary-hover)',
              cursor: 'pointer',
            }}
            onClick={(e) => {
              e.stopPropagation();
              navigator.clipboard?.writeText(activeServer.inviteCode || '');
              alert(`Copied server invite code: ${activeServer.inviteCode}`);
            }}
            title="Click to copy invite code"
          >
            Code: {activeServer.inviteCode}
          </span>
        )}
      </div>

      {/* Channel list */}
      <div className="channel-list">
        {activeServer && (
          <>
            <div className="channel-category">
              <span>Text Channels</span>
              <span
                style={{ cursor: 'pointer', fontSize: '16px', lineHeight: 1 }}
                onClick={onOpenCreateChannel}
                title="Create Channel"
              >
                +
              </span>
            </div>

            {activeServer.channels && activeServer.channels.length > 0 ? (
              activeServer.channels.map((ch) => {
                const isActive = activeChannel?.id === ch.id;
                return (
                  <div
                    key={ch.id}
                    className={`channel-item ${isActive ? 'active' : ''}`}
                    onClick={() => handleSelectChannel(ch)}
                  >
                    <span className="hash">#</span>
                    <span>{ch.name}</span>
                  </div>
                );
              })
            ) : (
              <div style={{ padding: '12px 16px', fontSize: '13px', color: 'var(--text-muted)' }}>
                No channels yet.
              </div>
            )}
          </>
        )}
      </div>

      {/* User info panel at bottom */}
      <div className="user-panel">
        <div className="user-avatar">
          {(user?.username || 'U').charAt(0).toUpperCase()}
        </div>
        <div className="user-info">
          <div className="name">{user?.username || 'Anonymous'}</div>
          <div className="status">Online</div>
        </div>
        <button
          className="btn btn-sm"
          style={{ background: 'transparent', color: 'var(--text-muted)' }}
          onClick={logout}
          title="Sign out"
        >
          Logout
        </button>
      </div>
    </div>
  );
};
