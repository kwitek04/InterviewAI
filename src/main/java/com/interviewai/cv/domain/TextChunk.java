package com.interviewai.cv.domain;

/**
 * A single contiguous slice of extracted CV text, identified by its position
 * in the original document's chunk sequence.
 */
public record TextChunk(int index, String text) {

    public TextChunk {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        text = Preconditions.requireNonBlank(text, "text");
    }
}
