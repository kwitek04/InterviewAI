package com.interviewai.interview.domain;

import com.interviewai.interview.application.InvalidQuestionResponseStatusTransitionException;

/**
 * Pure transition rules for {@link QuestionResponseStatus}.
 */
public final class QuestionResponseStatuses {

    private QuestionResponseStatuses() {
    }

    public static QuestionResponseStatus toStreaming(QuestionResponseStatus current) {
        return switch (current) {
            case PENDING -> QuestionResponseStatus.STREAMING;
            case STREAMING, COMPLETED, FAILED, CANCELLED ->
                    throw new InvalidQuestionResponseStatusTransitionException(current, QuestionResponseStatus.STREAMING);
        };
    }

    public static QuestionResponseStatus toCompleted(QuestionResponseStatus current) {
        return switch (current) {
            case STREAMING -> QuestionResponseStatus.COMPLETED;
            case PENDING, COMPLETED, FAILED, CANCELLED ->
                    throw new InvalidQuestionResponseStatusTransitionException(current, QuestionResponseStatus.COMPLETED);
        };
    }

    public static QuestionResponseStatus toFailed(QuestionResponseStatus current) {
        return switch (current) {
            case PENDING, STREAMING -> QuestionResponseStatus.FAILED;
            case COMPLETED, FAILED, CANCELLED ->
                    throw new InvalidQuestionResponseStatusTransitionException(current, QuestionResponseStatus.FAILED);
        };
    }

    public static QuestionResponseStatus toCancelled(QuestionResponseStatus current) {
        return switch (current) {
            case PENDING, STREAMING -> QuestionResponseStatus.CANCELLED;
            case COMPLETED, FAILED, CANCELLED ->
                    throw new InvalidQuestionResponseStatusTransitionException(current, QuestionResponseStatus.CANCELLED);
        };
    }
}
