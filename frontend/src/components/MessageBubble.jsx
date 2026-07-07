function MessageBubble({ role, content }) {
  const isCandidate = role === 'CANDIDATE';

  return (
    <div className={`message-row ${isCandidate ? 'message-row--candidate' : 'message-row--interviewer'}`}>
      {!isCandidate && <span className="avatar avatar--sm">AI</span>}
      <div
        className={`message-bubble ${
          isCandidate ? 'message-bubble--candidate' : 'message-bubble--interviewer'
        }`}
      >
        {content}
      </div>
    </div>
  );
}

export default MessageBubble;
