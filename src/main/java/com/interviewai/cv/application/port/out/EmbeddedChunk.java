package com.interviewai.cv.application.port.out;

import java.util.Objects;

/**
 * A CV chunk together with its vector embedding, ready for persistence.
 */
public record EmbeddedChunk(int index, String content, float[] embedding) {

    public EmbeddedChunk {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        Objects.requireNonNull(content, "content must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        Objects.requireNonNull(embedding, "embedding must not be null");
        if (embedding.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        embedding = embedding.clone();
    }

    @Override
    public float[] embedding() {
        return embedding.clone();
    }
}
