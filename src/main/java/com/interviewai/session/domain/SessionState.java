package com.interviewai.session.domain;

/**
 * The lifecycle states an {@link InterviewSession} can be in.
 */
public sealed interface SessionState {

    record Created() implements SessionState {
    }

    record InProgress() implements SessionState {
    }

    record AwaitingAnswer() implements SessionState {
    }

    record Completed() implements SessionState {
    }
}
