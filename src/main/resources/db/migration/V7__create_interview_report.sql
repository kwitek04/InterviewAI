CREATE TABLE interview_report (
    id               UUID PRIMARY KEY,
    session_id       UUID         NOT NULL UNIQUE REFERENCES interview_session (id),
    status           VARCHAR(32)  NOT NULL,
    payload          JSONB,
    failure_message  TEXT,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    generated_at     TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX idx_interview_report_status ON interview_report (status);
