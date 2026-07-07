function StartScreen({ onStart, isLoading, error }) {
  return (
    <div className="start-screen">
      <div className="start-layout">
        <section className="start-hero">
          <p className="start-kicker">Technical interview simulator</p>
          <h1 className="start-title">
            Interview<span className="start-title-accent">AI</span>
          </h1>
          <p className="start-lead">
            Practice answering technical interview questions with an AI recruiter.
          </p>
          <ul className="start-features">
            <li>Focused, one-question-at-a-time flow</li>
            <li>Follow-up questions based on your answers</li>
          </ul>
        </section>

        <aside className="start-panel">
          <h2 className="start-panel-title">Start your session</h2>
          <p className="start-panel-text">
            You will receive your first question as soon as the session starts. Answer in your own
            words.
          </p>
          <button className="start-button" onClick={onStart} disabled={isLoading}>
            {isLoading ? 'Starting…' : 'Start interview'}
          </button>
          {error && <p className="start-error">{error}</p>}
        </aside>
      </div>
    </div>
  );
}

export default StartScreen;
