package com.interviewai.shared;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Cross-module fact published when an interview session has been completed.
 * <p>
 * Lives in the shared module because it is referenced by the session lifecycle and
 * by downstream report generation.
 */
public record InterviewCompletedEvent(UUID eventId, SessionId sessionId, Instant completedAt) {

    public InterviewCompletedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(completedAt, "completedAt must not be null");
    }
}
