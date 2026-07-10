package com.interviewai.interview.domain;

/**
 * Lifecycle status of one generated interviewer response.
 */
public enum QuestionResponseStatus {
    PENDING,
    STREAMING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isActive() {
        return this == PENDING || this == STREAMING;
    }

    public boolean isTerminal() {
        return !isActive();
    }
}
