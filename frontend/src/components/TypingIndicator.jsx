function TypingIndicator({ label }) {
  return (
    <div className="message-row message-row--interviewer">
      <span className="avatar avatar--sm">AI</span>
      <div className="typing-indicator">
        <span className="typing-dot" />
        <span className="typing-dot" />
        <span className="typing-dot" />
        {label && <span className="typing-label">{label}</span>}
      </div>
    </div>
  );
}

export default TypingIndicator;
