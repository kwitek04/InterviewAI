import { useCallback, useEffect, useRef } from 'react';
import MessageBubble from './MessageBubble.jsx';
import ChatInput from './ChatInput.jsx';
import { useResponseEventStream } from '../hooks/useResponseEventStream.js';

function ChatScreen({
  messages,
  streamTarget,
  isStreaming,
  isReconnecting,
  error,
  onSendAnswer,
  onRestart,
  onToken,
  onCompleted,
  onServerError,
  onReconnecting,
}) {
  const bottomRef = useRef(null);
  const wasStreamingRef = useRef(isStreaming);

  useResponseEventStream(streamTarget, {
    onToken,
    onCompleted,
    onServerError,
    onReconnecting,
  });

  const scrollToBottom = useCallback((behavior = 'auto') => {
    bottomRef.current?.scrollIntoView({ behavior, block: 'end' });
  }, []);

  useEffect(() => {
    const behavior = isStreaming && wasStreamingRef.current ? 'auto' : 'smooth';
    scrollToBottom(behavior);
    wasStreamingRef.current = isStreaming;
  }, [messages, isStreaming, isReconnecting, scrollToBottom]);

  const statusLabel = isReconnecting
    ? 'Reconnecting…'
    : isStreaming
      ? 'typing…'
      : 'online';

  return (
    <div className="chat-shell">
      <div className="chat-screen">
        <header className="chat-header">
          <div className="chat-header-info">
            <span className="avatar avatar--sm">AI</span>
            <div className="chat-header-text">
              <h2>AI Recruiter</h2>
              <span className="chat-status">
                <span
                  className={`status-dot ${
                    isStreaming || isReconnecting ? 'status-dot--busy' : 'status-dot--online'
                  }`}
                />
                {statusLabel}
              </span>
            </div>
          </div>
          <button className="restart-button" onClick={onRestart} title="Start a new interview">
            New interview
          </button>
        </header>

        <div className="chat-messages">
          {messages.map((message) => (
            <MessageBubble
              key={message.responseId ?? message.id}
              role={message.role}
              content={message.content}
              isStreaming={message.isStreaming}
              onRevealProgress={() => scrollToBottom('auto')}
            />
          ))}
          <div ref={bottomRef} />
        </div>

        {error && <div className="chat-error">{error}</div>}

        <ChatInput onSend={onSendAnswer} disabled={isStreaming} isStreaming={isStreaming} />
      </div>
    </div>
  );
}

export default ChatScreen;
