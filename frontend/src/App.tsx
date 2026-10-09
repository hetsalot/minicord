import React, { useState } from 'react';
import { useAuth } from './context/AuthContext';
import { ChatProvider } from './context/ChatContext';
import { AuthPage } from './components/Auth/AuthPage';
import { ServerSidebar } from './components/Sidebar/ServerSidebar';
import { ChannelSidebar } from './components/Sidebar/ChannelSidebar';
import { ChatArea } from './components/Chat/ChatArea';
import { MemberList } from './components/Sidebar/MemberList';
import { AdminDashboard } from './components/Admin/AdminDashboard';
import { CreateServerModal } from './components/Modals/CreateServerModal';
import { JoinServerModal } from './components/Modals/JoinServerModal';
import { CreateChannelModal } from './components/Modals/CreateChannelModal';

const MainLayout: React.FC = () => {
  const [currentView, setCurrentView] = useState<'chat' | 'admin'>('chat');
  const [isCreateServerOpen, setIsCreateServerOpen] = useState(false);
  const [isJoinServerOpen, setIsJoinServerOpen] = useState(false);
  const [isCreateChannelOpen, setIsCreateChannelOpen] = useState(false);

  return (
    <div className="app-layout">
      {/* 1. Leftmost icon sidebar */}
      <ServerSidebar
        currentView={currentView}
        setCurrentView={setCurrentView}
        onOpenCreateServer={() => setIsCreateServerOpen(true)}
        onOpenJoinServer={() => setIsJoinServerOpen(true)}
      />

      {/* 2. Main content based on currentView */}
      {currentView === 'chat' ? (
        <>
          <ChannelSidebar
            onOpenCreateChannel={() => setIsCreateChannelOpen(true)}
          />
          <ChatArea />
          <MemberList />
        </>
      ) : (
        <AdminDashboard />
      )}

      {/* Modals */}
      <CreateServerModal
        isOpen={isCreateServerOpen}
        onClose={() => setIsCreateServerOpen(false)}
      />
      <JoinServerModal
        isOpen={isJoinServerOpen}
        onClose={() => setIsJoinServerOpen(false)}
      />
      <CreateChannelModal
        isOpen={isCreateChannelOpen}
        onClose={() => setIsCreateChannelOpen(false)}
      />
    </div>
  );
};

export const App: React.FC = () => {
  const { user, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div style={{ height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-primary)', color: 'var(--text-muted)' }}>
        Loading MiniCord...
      </div>
    );
  }

  if (!user) {
    return <AuthPage />;
  }

  return (
    <ChatProvider>
      <MainLayout />
    </ChatProvider>
  );
};

export default App;
