package com.interviewai.session.application;

import com.interviewai.shared.SessionId;

/**
 * Thrown when no {@code InterviewSession} exists for a given {@link SessionId}.
 */
public final class SessionNotFoundException extends RuntimeException {

    private final SessionId sessionId;

    public SessionNotFoundException(SessionId sessionId) {
        super("No interview session found with id " + sessionId.value());
        this.sessionId = sessionId;
    }

    public SessionId sessionId() {
        return sessionId;
    }
}
