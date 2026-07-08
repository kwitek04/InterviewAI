package com.interviewai.cv.application.port.out;

import java.util.Objects;

/**
 * Describes a file that has been persisted through a {@link FileStorage} adapter.
 */
public record StoredFile(String key, long sizeBytes) {

    public StoredFile {
        Objects.requireNonNull(key, "key must not be null");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
    }
}
