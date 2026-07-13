package com.interviewai.interview.application;

/**
 * Thrown when interviewer question generation fails or returns unusable output.
 */
public final class QuestionGenerationException extends RuntimeException {

    public QuestionGenerationException(String message) {
        super(message);
    }

    public QuestionGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
