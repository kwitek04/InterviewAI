package com.interviewai.interview.application;

import com.interviewai.shared.SessionId;

/**
 * Thrown when a session already has an active generated response.
 */
public final class ActiveQuestionResponseAlreadyExistsException extends RuntimeException {

    private final SessionId sessionId;

    public ActiveQuestionResponseAlreadyExistsException(SessionId sessionId) {
        super("Session already has an active question response: " + sessionId.value());
        this.sessionId = sessionId;
    }

    public SessionId sessionId() {
        return sessionId;
    }
}
