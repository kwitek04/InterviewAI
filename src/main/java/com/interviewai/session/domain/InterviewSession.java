package com.interviewai.session.domain;

import com.interviewai.shared.SessionId;

import java.util.Objects;

/**
 * Aggregate root modeling the lifecycle of a single AI-driven interview conversation.
 * <p>
 * Instances are immutable: {@link #apply(SessionCommand)} never mutates {@code this}.
 * It either returns a new instance reflecting the transition or throws
 * {@link SessionTransitionException} when the command is not valid for the current state.
 */
public record InterviewSession(SessionId id, SessionState state, Transcript transcript) {

    public InterviewSession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(transcript, "transcript must not be null");
    }

    public static InterviewSession create(SessionId id) {
        return new InterviewSession(id, new SessionState.Created(), Transcript.empty());
    }

    /**
     * Applies a command to this session, producing its next state.
     * <p>
     * {@link SessionState.Completed} and {@link SessionState.Cancelled} are terminal:
     * every command applied to a session in either state throws
     * {@link SessionTransitionException}, including a repeated end or cancel.
     * Any other command that is invalid for the current state throws the same exception.
     */
    public InterviewSession apply(SessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return switch (state) {
            case SessionState.Created() -> applyToCreated(command);
            case SessionState.InProgress() -> applyToInProgress(command);
            case SessionState.AwaitingAnswer() -> applyToAwaitingAnswer(command);
            case SessionState.Completed() -> applyToTerminalState(command);
            case SessionState.Cancelled() -> applyToTerminalState(command);
        };
    }

    private InterviewSession applyToCreated(SessionCommand command) {
        return switch (command) {
            case SessionCommand.StartInterview() -> withState(new SessionState.InProgress());
            case SessionCommand.CancelInterview() -> withState(new SessionState.Cancelled());
            default -> throw rejectedBy(command);
        };
    }

    private InterviewSession applyToInProgress(SessionCommand command) {
        return switch (command) {
            case SessionCommand.AskQuestion askQuestion -> withStateAndMessage(
                    new SessionState.AwaitingAnswer(),
                    new Message(MessageRole.INTERVIEWER, askQuestion.content(), askQuestion.askedAt()));
            case SessionCommand.CancelInterview() -> withState(new SessionState.Cancelled());
            default -> throw rejectedBy(command);
        };
    }

    private InterviewSession applyToAwaitingAnswer(SessionCommand command) {
        return switch (command) {
            case SessionCommand.SubmitAnswer submitAnswer -> withStateAndMessage(
                    new SessionState.InProgress(),
                    new Message(MessageRole.CANDIDATE, submitAnswer.content(), submitAnswer.answeredAt()));
            case SessionCommand.EndInterview() -> withState(new SessionState.Completed());
            case SessionCommand.CancelInterview() -> withState(new SessionState.Cancelled());
            default -> throw rejectedBy(command);
        };
    }

    private InterviewSession applyToTerminalState(SessionCommand command) {
        throw rejectedBy(command);
    }

    private InterviewSession withState(SessionState newState) {
        return new InterviewSession(id, newState, transcript);
    }

    private InterviewSession withStateAndMessage(SessionState newState, Message message) {
        return new InterviewSession(id, newState, transcript.append(message));
    }

    private SessionTransitionException rejectedBy(SessionCommand command) {
        return new SessionTransitionException(state, command);
    }
}
