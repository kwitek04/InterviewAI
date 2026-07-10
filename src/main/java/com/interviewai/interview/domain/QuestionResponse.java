package com.interviewai.interview.domain;

import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;

import java.time.Instant;
import java.util.Objects;

/**
 * Metadata describing one generated interviewer response and its stream state.
 */
public record QuestionResponse(
        ResponseId id,
        SessionId sessionId,
        QuestionResponseStatus status,
        String accumulatedText,
        String finalText,
        int lastEventSequence,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt) {

    public QuestionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(accumulatedText, "accumulatedText must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
