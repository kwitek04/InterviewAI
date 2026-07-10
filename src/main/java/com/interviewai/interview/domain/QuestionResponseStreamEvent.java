package com.interviewai.interview.domain;

import com.interviewai.shared.ResponseId;

import java.time.Instant;
import java.util.Objects;

/**
 * One ordered event belonging to an interviewer response stream.
 */
public record QuestionResponseStreamEvent(
        ResponseId responseId,
        int sequence,
        QuestionResponseEventType type,
        String payload,
        Instant createdAt) {

    public QuestionResponseStreamEvent {
        Objects.requireNonNull(responseId, "responseId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
    }
}
