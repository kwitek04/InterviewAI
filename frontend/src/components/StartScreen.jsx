import { useCallback, useRef, useState } from 'react';

function isPdfFile(file) {
  return file && (file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf'));
}

function StartScreen({ onStart, isLoading, error }) {
  const [cvFile, setCvFile] = useState(null);
  const [jobOffer, setJobOffer] = useState('');
  const [fileError, setFileError] = useState(null);
  const [isDragging, setIsDragging] = useState(false);
  const fileInputRef = useRef(null);

  const canSubmit = cvFile && jobOffer.trim().length > 0 && !isLoading;

  const selectFile = useCallback((file) => {
    if (!file) {
      return;
    }
    if (!isPdfFile(file)) {
      setFileError('Only PDF files are accepted.');
      setCvFile(null);
      return;
    }
    setFileError(null);
    setCvFile(file);
  }, []);

  const handleFileInputChange = useCallback(
    (event) => {
      selectFile(event.target.files?.[0] ?? null);
    },
    [selectFile],
  );

  const handleDragOver = useCallback((event) => {
    event.preventDefault();
    if (!isLoading) {
      setIsDragging(true);
    }
  }, [isLoading]);

  const handleDragLeave = useCallback((event) => {
    event.preventDefault();
    setIsDragging(false);
  }, []);

  const handleDrop = useCallback(
    (event) => {
      event.preventDefault();
      setIsDragging(false);
      if (isLoading) {
        return;
      }
      selectFile(event.dataTransfer.files?.[0] ?? null);
    },
    [isLoading, selectFile],
  );

  const handleSubmit = useCallback(
    (event) => {
      event.preventDefault();
      if (!canSubmit) {
        return;
      }
      onStart({ file: cvFile, jobOffer: jobOffer.trim() });
    },
    [canSubmit, cvFile, jobOffer, onStart],
  );

  return (
    <div className="start-screen">
      <div className="start-layout">
        <section className="start-hero">
          <p className="start-kicker">Technical interview simulator</p>
          <h1 className="start-title">
            Interview<span className="start-title-accent">AI</span>
          </h1>
          <p className="start-lead">
            Upload your CV and paste a job offer to practice a technical interview grounded in your
            real experience.
          </p>
          <ul className="start-features">
            <li>Questions tailored to your CV and the role</li>
            <li>RAG-backed context from uploaded documents</li>
            <li>Focused, one-question-at-a-time flow</li>
          </ul>
        </section>

        <aside className="start-panel">
          <h2 className="start-panel-title">Prepare your session</h2>
          <p className="start-panel-text">
            We will process your CV and start an interview session linked to
            that context.
          </p>

          <form className="start-form" onSubmit={handleSubmit} noValidate>
            <label className="start-field">
              <span className="start-label">Your CV</span>
              <div
                className={`start-dropzone${isDragging ? ' start-dropzone--active' : ''}${cvFile ? ' start-dropzone--filled' : ''}`}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                onClick={() => !isLoading && fileInputRef.current?.click()}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    if (!isLoading) {
                      fileInputRef.current?.click();
                    }
                  }
                }}
                role="button"
                tabIndex={isLoading ? -1 : 0}
                aria-disabled={isLoading}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf,application/pdf"
                  className="start-file-input"
                  onChange={handleFileInputChange}
                  disabled={isLoading}
                  tabIndex={-1}
                />
                {cvFile ? (
                  <>
                    <span className="start-dropzone-filename">{cvFile.name}</span>
                    <span className="start-dropzone-hint">Click or drop to replace</span>
                  </>
                ) : (
                  <>
                    <span className="start-dropzone-title">Drop your CV</span>
                    <span className="start-dropzone-hint">or click to browse - PDF only</span>
                  </>
                )}
              </div>
              {fileError && <p className="start-field-error">{fileError}</p>}
            </label>

            <label className="start-field">
              <span className="start-label">Job offer</span>
              <textarea
                className="start-textarea"
                value={jobOffer}
                onChange={(event) => setJobOffer(event.target.value)}
                placeholder="Paste the job description, required skills, and responsibilities"
                rows={7}
                disabled={isLoading}
                required
              />
            </label>

            <button className="start-button" type="submit" disabled={!canSubmit}>
              {isLoading ? (
                <span className="start-button-content">
                  <span className="start-spinner" aria-hidden="true" />
                  Processing CV…
                </span>
              ) : (
                'Start interview'
              )}
            </button>

            {isLoading && (
              <p className="start-loading-note">
                Uploading your CV. This may take a moment.
              </p>
            )}
            {error && <p className="start-error">{error}</p>}
          </form>
        </aside>
      </div>
    </div>
  );
}

export default StartScreen;
