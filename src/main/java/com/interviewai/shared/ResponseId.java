package com.interviewai.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Globally unique identifier of one generated interviewer response stream.
 */
public record ResponseId(UUID value) {

    public ResponseId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ResponseId generate() {
        return new ResponseId(UUID.randomUUID());
    }
}
