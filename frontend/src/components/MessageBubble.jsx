import { useWordReveal } from '../hooks/useWordReveal.js';

function MessageBubble({ role, content, isStreaming, onRevealProgress }) {
  const isCandidate = role === 'CANDIDATE';
  const { displayText, isRevealing } = useWordReveal(content, !isCandidate && isStreaming, {
    onSegmentRevealed: onRevealProgress,
  });
  const showCursor = !isCandidate && (isStreaming || isRevealing);

  return (
    <div className={`message-row ${isCandidate ? 'message-row--candidate' : 'message-row--interviewer'}`}>
      {!isCandidate && <span className="avatar avatar--sm">AI</span>}
      <div
        className={`message-bubble ${
          isCandidate ? 'message-bubble--candidate' : 'message-bubble--interviewer'
        }`}
      >
        {isCandidate ? content : displayText}
        {showCursor && <span className="streaming-cursor" aria-hidden="true" />}
      </div>
    </div>
  );
}

export default MessageBubble;
