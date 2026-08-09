CREATE TABLE processed_event (
    event_id       UUID PRIMARY KEY,
    session_id     UUID         NOT NULL REFERENCES interview_session (id),
    status         VARCHAR(32)  NOT NULL,
    claim_owner    VARCHAR(128),
    claimed_at     TIMESTAMPTZ,
    attempt_count  INT          NOT NULL DEFAULT 0,
    completed_at   TIMESTAMPTZ
);

CREATE INDEX idx_processed_event_session_id ON processed_event (session_id);
CREATE INDEX idx_processed_event_status ON processed_event (status);
