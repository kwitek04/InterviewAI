CREATE TABLE question_response (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_session (id),
    status VARCHAR(32) NOT NULL,
    accumulated_text TEXT NOT NULL DEFAULT '',
    final_text TEXT,
    last_event_sequence INTEGER NOT NULL DEFAULT 0,
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE question_response_event (
    response_id UUID NOT NULL REFERENCES question_response (id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (response_id, sequence)
);

CREATE INDEX idx_question_response_event_replay
    ON question_response_event (response_id, sequence);

CREATE UNIQUE INDEX uq_question_response_one_active_per_session
    ON question_response (session_id)
    WHERE status IN ('PENDING', 'STREAMING');
