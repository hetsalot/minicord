import React from 'react';
import { useChat } from '../../context/ChatContext';
import { Server } from '../../types';

interface ServerSidebarProps {
  currentView: 'chat' | 'admin';
  setCurrentView: (view: 'chat' | 'admin') => void;
  onOpenCreateServer: () => void;
  onOpenJoinServer: () => void;
}

export const ServerSidebar: React.FC<ServerSidebarProps> = ({
  currentView,
  setCurrentView,
  onOpenCreateServer,
  onOpenJoinServer,
}) => {
  const { servers, activeServer, setActiveServer } = useChat();

  const handleSelectServer = (srv: Server) => {
    setActiveServer(srv);
    setCurrentView('chat');
  };

  return (
    <div className="server-sidebar">
      {/* MiniCord Home / Chat button */}
      <div
        className={`server-icon home ${currentView === 'chat' && !activeServer ? 'active' : ''}`}
        onClick={() => setCurrentView('chat')}
        title="MiniCord Home"
      >
        MC
      </div>

      {/* DC Admin Dashboard shortcut */}
      <div
        className={`server-icon ${currentView === 'admin' ? 'active' : ''}`}
        style={{
          background: currentView === 'admin' ? 'var(--accent-primary)' : 'rgba(99, 102, 241, 0.2)',
          color: '#818cf8',
        }}
        onClick={() => setCurrentView('admin')}
        title="Distributed Computing Admin Dashboard"
      >
        ⚡
      </div>

      <div className="server-divider" />

      {/* Server icons */}
      {servers.map((srv) => {
        const initial = srv.name.substring(0, 2).toUpperCase();
        const isActive = currentView === 'chat' && activeServer?.id === srv.id;

        return (
          <div
            key={srv.id}
            className={`server-icon ${isActive ? 'active' : ''}`}
            onClick={() => handleSelectServer(srv)}
            title={srv.name}
          >
            {initial}
          </div>
        );
      })}

      {/* Add Server (+) */}
      <div
        className="server-icon add"
        onClick={onOpenCreateServer}
        title="Create a Server"
      >
        +
      </div>

      {/* Join Server (🔗) */}
      <div
        className="server-icon"
        style={{
          background: 'transparent',
          border: '2px dashed rgba(255, 255, 255, 0.2)',
          fontSize: '16px',
        }}
        onClick={onOpenJoinServer}
        title="Join Server with Invite Code"
      >
        🔗
      </div>
    </div>
  );
};
