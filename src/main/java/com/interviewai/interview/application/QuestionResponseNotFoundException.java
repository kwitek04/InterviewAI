package com.interviewai.interview.application;

import com.interviewai.shared.ResponseId;

/**
 * Thrown when a {@link com.interviewai.interview.domain.QuestionResponse} cannot be found.
 */
public final class QuestionResponseNotFoundException extends RuntimeException {

    private final ResponseId responseId;

    public QuestionResponseNotFoundException(ResponseId responseId) {
        super("Question response not found: " + responseId.value());
        this.responseId = responseId;
    }

    public ResponseId responseId() {
        return responseId;
    }
}
