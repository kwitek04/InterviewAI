package com.interviewai.session.adapter.out.persistence;

import com.interviewai.session.domain.SessionState;

/**
 * Translates between the domain {@link SessionState} hierarchy and its stored
 * string representation.
 */
final class SessionStateMapper {

    private static final String CREATED = "CREATED";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String AWAITING_ANSWER = "AWAITING_ANSWER";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";

    private SessionStateMapper() {
    }

    static String toStorage(SessionState state) {
        return switch (state) {
            case SessionState.Created() -> CREATED;
            case SessionState.InProgress() -> IN_PROGRESS;
            case SessionState.AwaitingAnswer() -> AWAITING_ANSWER;
            case SessionState.Completed() -> COMPLETED;
            case SessionState.Cancelled() -> CANCELLED;
        };
    }

    static SessionState fromStorage(String state) {
        return switch (state) {
            case CREATED -> new SessionState.Created();
            case IN_PROGRESS -> new SessionState.InProgress();
            case AWAITING_ANSWER -> new SessionState.AwaitingAnswer();
            case COMPLETED -> new SessionState.Completed();
            case CANCELLED -> new SessionState.Cancelled();
            default -> throw new IllegalStateException("Unknown persisted session state: " + state);
        };
    }
}
