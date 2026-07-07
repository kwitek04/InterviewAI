import { useEffect, useRef } from 'react';
import MessageBubble from './MessageBubble.jsx';
import TypingIndicator from './TypingIndicator.jsx';
import ChatInput from './ChatInput.jsx';

function ChatScreen({ messages, isLoading, error, onSendAnswer, onRestart }) {
  const bottomRef = useRef(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  return (
    <div className="chat-shell">
      <div className="chat-screen">
        <header className="chat-header">
          <div className="chat-header-info">
            <span className="avatar avatar--sm">AI</span>
            <div className="chat-header-text">
              <h2>AI Recruiter</h2>
              <span className="chat-status">
                <span className={`status-dot ${isLoading ? 'status-dot--busy' : 'status-dot--online'}`} />
                {isLoading ? 'reviewing your answer' : 'online'}
              </span>
            </div>
          </div>
          <button className="restart-button" onClick={onRestart} title="Start a new interview">
            New interview
          </button>
        </header>

        <div className="chat-messages">
          {messages.map((message) => (
            <MessageBubble key={message.id} role={message.role} content={message.content} />
          ))}
          {isLoading && <TypingIndicator label="Recruiter is reviewing your answer…" />}
          <div ref={bottomRef} />
        </div>

        {error && <div className="chat-error">{error}</div>}

        <ChatInput onSend={onSendAnswer} disabled={isLoading} />
      </div>
    </div>
  );
}

export default ChatScreen;
