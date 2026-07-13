package com.interviewai.interview.application;

import com.interviewai.interview.domain.QuestionResponseStatus;

/**
 * Thrown when a {@link QuestionResponseStatus} transition is not allowed.
 */
public final class InvalidQuestionResponseStatusTransitionException extends RuntimeException {

    private final QuestionResponseStatus fromStatus;
    private final QuestionResponseStatus toStatus;

    public InvalidQuestionResponseStatusTransitionException(
            QuestionResponseStatus fromStatus,
            QuestionResponseStatus toStatus) {
        super("Cannot transition question response from %s to %s".formatted(fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public QuestionResponseStatus fromStatus() {
        return fromStatus;
    }

    public QuestionResponseStatus toStatus() {
        return toStatus;
    }
}
