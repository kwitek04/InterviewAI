function AssessmentRow({ assessment }) {
  return (
    <article className="assessment-row">
      <header className="assessment-row-header">
        <h3 className="assessment-question">
          Question {assessment.questionIndex + 1}
        </h3>
        <p className="assessment-score" aria-label={`Score ${assessment.score} out of 5, ${assessment.scoreLabel}`}>
          <span className={`score-badge score-badge--${assessment.score}`}>{assessment.score}/5</span>
          <span className="score-label">{assessment.scoreLabel}</span>
        </p>
      </header>
      <dl className="assessment-details">
        <div>
          <dt>Question</dt>
          <dd>{assessment.question}</dd>
        </div>
        <div>
          <dt>Your answer</dt>
          <dd>{assessment.answer}</dd>
        </div>
        <div>
          <dt>Rationale</dt>
          <dd>{assessment.rationale}</dd>
        </div>
      </dl>
    </article>
  );
}

function InsightList({ title, items }) {
  return (
    <section className="insight-block" aria-labelledby={`${title}-heading`}>
      <h2 id={`${title}-heading`} className="insight-title">
        {title}
      </h2>
      <ul className="insight-list">
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </section>
  );
}

function ReportScreen({ report, onRestart }) {
  return (
    <div className="report-shell">
      <div className="report-layout">
        <header className="report-header">
          <div>
            <p className="report-kicker">Interview complete</p>
            <h1 className="report-title">Your feedback report</h1>
            <p className="report-lead">
              Here is how your answers were scored, plus strengths, gaps, and next steps.
            </p>
          </div>
          <button type="button" className="start-button report-action-button" onClick={onRestart}>
            Start a new interview
          </button>
        </header>

        <section className="assessment-section" aria-labelledby="assessments-heading">
          <h2 id="assessments-heading" className="section-heading">
            Question assessments
          </h2>
          <div className="assessment-list">
            {report.assessments.map((assessment) => (
              <AssessmentRow key={assessment.questionIndex} assessment={assessment} />
            ))}
          </div>
        </section>

        <div className="insight-grid">
          <InsightList title="Strengths" items={report.strengths} />
          <InsightList title="Weaknesses" items={report.weaknesses} />
          <InsightList title="Recommendations" items={report.recommendations} />
        </div>
      </div>
    </div>
  );
}

export default ReportScreen;
