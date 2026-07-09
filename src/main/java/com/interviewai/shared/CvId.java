package com.interviewai.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Globally unique identifier of an uploaded CV document.
 * <p>
 * Lives in the shared module because it is referenced across module boundaries.
 */
public record CvId(UUID value) {

    public CvId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CvId generate() {
        return new CvId(UUID.randomUUID());
    }
}
