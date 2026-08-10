package com.interviewai.session.application;

import com.interviewai.session.domain.SessionState;
import com.interviewai.shared.SessionId;

/**
 * Thrown when a completed-interview snapshot is requested for a session that is not
 * in {@link SessionState.Completed} or {@link SessionState.ReportReady}.
 */
public class SessionNotCompletedException extends RuntimeException {

    private final SessionId sessionId;
    private final SessionState state;

    public SessionNotCompletedException(SessionId sessionId, SessionState state) {
        super("Session " + sessionId.value() + " is not completed (state=" + state.getClass().getSimpleName() + ")");
        this.sessionId = sessionId;
        this.state = state;
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public SessionState state() {
        return state;
    }
}
