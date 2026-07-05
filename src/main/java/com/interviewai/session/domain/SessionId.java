package com.interviewai.session.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Globally unique identifier of an {@link InterviewSession}.
 */
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
    }
}
