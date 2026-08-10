function ReportGeneratingScreen({ status, error, onRestart }) {
  const label = status === 'GENERATING' ? 'Scoring your answers…' : 'Preparing your report…';

  return (
    <div className="report-shell">
      <section className="report-panel report-panel--generating" aria-live="polite">
        <p className="report-kicker">Interview complete</p>
        <h1 className="report-title">Generating feedback</h1>
        <p className="report-lead">
          Your answers are being scored and summarised. This usually takes a short moment.
        </p>
        <div className="report-progress" role="status">
          <span className="report-progress-dot" aria-hidden="true" />
          <span>{error ? 'Retrying connection…' : label}</span>
        </div>
        {error && <p className="report-inline-error">{error}</p>}
        <button type="button" className="restart-button report-secondary-action" onClick={onRestart}>
          Start a new interview
        </button>
      </section>
    </div>
  );
}

export default ReportGeneratingScreen;
