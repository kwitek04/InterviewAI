package com.interviewai.cv.application.port.out;

import java.util.Objects;

/**
 * A retrieved CV chunk paired with its similarity score.
 */
public record ScoredChunk(String content, double score) {

    public ScoredChunk {
        Objects.requireNonNull(content, "content must not be null");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
