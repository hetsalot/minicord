import React from 'react';
import { useChat } from '../../context/ChatContext';

export const MemberList: React.FC = () => {
  const { activeServer, members } = useChat();

  if (!activeServer) return null;

  // If no members list provided from server yet, show demo cluster members
  const displayMembers = members.length > 0 ? members : [
    { id: '1', username: 'ClusterCoordinator', status: 'online' as const },
    { id: '2', username: 'ServerNode1', status: 'online' as const },
    { id: '3', username: 'ServerNode2', status: 'online' as const },
    { id: '4', username: 'ServerNode3', status: 'online' as const },
  ];

  return (
    <div className="member-sidebar">
      <div className="member-section-title">
        Online — {displayMembers.length}
      </div>
      {displayMembers.map((m) => (
        <div key={m.id} className="member-item">
          <div className={`member-avatar-sm ${m.status || 'online'}`}>
            {m.username.charAt(0).toUpperCase()}
          </div>
          <div className="member-name">{m.username}</div>
        </div>
      ))}
    </div>
  );
};
