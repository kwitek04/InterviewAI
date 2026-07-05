package com.interviewai.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Globally unique identifier of an interview session.
 * <p>
 * Lives in the shared module because it is referenced across module boundaries.
 */
public record SessionId(UUID value) {

    public SessionId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static SessionId generate() {
        return new SessionId(UUID.randomUUID());
    }
}
