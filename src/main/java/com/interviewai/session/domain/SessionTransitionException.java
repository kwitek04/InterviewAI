package com.interviewai.session.domain;

/**
 * Thrown when a {@link SessionCommand} is not valid for the current {@link SessionState}
 * of an {@link InterviewSession}.
 */
public final class SessionTransitionException extends RuntimeException {

    private final SessionState fromState;
    private final SessionCommand rejectedCommand;

    public SessionTransitionException(SessionState fromState, SessionCommand rejectedCommand) {
        super("%s is not a valid command in state %s".formatted(
                rejectedCommand.getClass().getSimpleName(),
                fromState.getClass().getSimpleName()));
        this.fromState = fromState;
        this.rejectedCommand = rejectedCommand;
    }

    public SessionState fromState() {
        return fromState;
    }

    public SessionCommand rejectedCommand() {
        return rejectedCommand;
    }
}
