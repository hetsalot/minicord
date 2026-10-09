import React from 'react';
import { Message } from '../../types';

interface MessageItemProps {
  message: Message;
}

export const MessageItem: React.FC<MessageItemProps> = ({ message }) => {
  const authorInitial = (message.author.username || 'U').charAt(0).toUpperCase();

  const formattedTime = new Date(message.createdAt).toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className="message">
      <div className="message-avatar">
        {authorInitial}
      </div>
      <div className="message-body">
        <div className="message-header">
          <span className="message-author">{message.author.username}</span>
          <span className="message-timestamp">{formattedTime}</span>
        </div>
        <div className="message-content">{message.content}</div>

        {/* Distributed Computing Clocks Metadata */}
        <div className="message-meta">
          <span title="Lamport Logical Clock Timestamp (Topic 3)">
            L: {message.lamportTimestamp}
          </span>
          <span title="Vector Clock [S1, S2, S3] (Topic 3)">
            V: [{Array.isArray(message.vectorClock) ? message.vectorClock.join(', ') : '0, 0, 0'}]
          </span>
          <span title="Node origin server that handled this message (Topic 7)">
            Origin: S{message.serverOrigin || 1}
          </span>
        </div>
      </div>
    </div>
  );
};
