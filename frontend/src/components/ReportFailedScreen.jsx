function ReportFailedScreen({ message, onRestart }) {
  return (
    <div className="report-shell">
      <section className="report-panel" aria-live="assertive">
        <p className="report-kicker">Report unavailable</p>
        <h1 className="report-title">We could not finish your feedback</h1>
        <p className="report-lead">{message}</p>
        <button type="button" className="start-button report-action-button" onClick={onRestart}>
          Start a new interview
        </button>
      </section>
    </div>
  );
}

export default ReportFailedScreen;
