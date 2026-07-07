import { useState } from 'react';

function ChatInput({ onSend, disabled }) {
  const [value, setValue] = useState('');

  const trySend = () => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setValue('');
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    trySend();
  };

  const handleKeyDown = (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      trySend();
    }
  };

  return (
    <form className="chat-input" onSubmit={handleSubmit}>
      <textarea
        value={value}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={disabled ? 'Recruiter is reviewing your answer…' : 'Type your answer…'}
        disabled={disabled}
        rows={1}
        aria-label="Your answer"
      />
      <button
        type="submit"
        className="send-button"
        disabled={disabled || !value.trim()}
        aria-label="Send answer"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M4 12L20 4L13 20L11 13L4 12Z"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinejoin="round"
            strokeLinecap="round"
          />
        </svg>
      </button>
    </form>
  );
}

export default ChatInput;
