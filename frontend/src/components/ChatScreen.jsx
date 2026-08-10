import { useCallback, useEffect, useRef } from 'react';
import MessageBubble from './MessageBubble.jsx';
import ChatInput from './ChatInput.jsx';
import { useResponseEventStream } from '../hooks/useResponseEventStream.js';

function ChatScreen({
  messages,
  streamTarget,
  isStreaming,
  isEnding,
  isReconnecting,
  error,
  onSendAnswer,
  onEndInterview,
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

  const endDisabled = isStreaming || isEnding;

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
          <div className="chat-header-actions">
            <button
              type="button"
              className="end-interview-button"
              onClick={onEndInterview}
              disabled={endDisabled}
              title={
                isStreaming
                  ? 'Wait until the current question finishes streaming'
                  : 'End the interview and generate feedback'
              }
            >
              {isEnding ? 'Ending…' : 'End interview'}
            </button>
            <button
              type="button"
              className="restart-button"
              onClick={onRestart}
              title="Start a new interview"
            >
              New interview
            </button>
          </div>
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

        <ChatInput
          onSend={onSendAnswer}
          disabled={isStreaming || isEnding}
          isStreaming={isStreaming}
        />
      </div>
    </div>
  );
}

export default ChatScreen;
