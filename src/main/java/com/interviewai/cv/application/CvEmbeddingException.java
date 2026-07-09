package com.interviewai.cv.application;

/**
 * Thrown when the embedding backend is unavailable or cannot process the
 * submitted text.
 */
public final class CvEmbeddingException extends RuntimeException {

    public CvEmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
