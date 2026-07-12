package com.interviewai.session.application;

import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;

import java.util.Objects;

/**
 * Handle returned when a command accepts a new interviewer response generation.
 */
public record AcceptedGeneration(SessionId sessionId, ResponseId responseId) {

    public AcceptedGeneration {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(responseId, "responseId must not be null");
    }
}
