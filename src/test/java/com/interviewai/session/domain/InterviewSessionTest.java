package com.interviewai.session.domain;

import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class InterviewSessionTest {

    private static final Instant QUESTION_TIME = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant ANSWER_TIME = Instant.parse("2026-01-01T10:01:00Z");

    @Test
    @DisplayName("starting a created session transitions it to InProgress with an empty transcript")
    void apply_startInterviewOnCreated_transitionsToInProgress() {
        InterviewSession created = InterviewSession.create(SessionId.generate());

        InterviewSession result = created.apply(new SessionCommand.StartInterview());

        assertThat(result.state()).isEqualTo(new SessionState.InProgress());
        assertThat(result.transcript().messages()).isEmpty();
        assertThat(created.state()).isEqualTo(new SessionState.Created());
    }

    @Test
    @DisplayName("asking a question moves an in-progress session to AwaitingAnswer and appends an interviewer message")
    void apply_askQuestionOnInProgress_transitionsToAwaitingAnswerAndAppendsInterviewerMessage() {
        InterviewSession inProgress = new InterviewSession(
                SessionId.generate(), new SessionState.InProgress(), Transcript.empty());

        InterviewSession result = inProgress.apply(
                new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME));

        assertThat(result.state()).isEqualTo(new SessionState.AwaitingAnswer());
        assertThat(result.transcript().messages())
                .containsExactly(new Message(MessageRole.INTERVIEWER, "Tell me about yourself", QUESTION_TIME));
    }

    @Test
    @DisplayName("submitting an answer moves an awaiting-answer session back to InProgress and appends a candidate message")
    void apply_submitAnswerOnAwaitingAnswer_transitionsToInProgressAndAppendsCandidateMessage() {
        Transcript existing = Transcript.empty()
                .append(new Message(MessageRole.INTERVIEWER, "Tell me about yourself", QUESTION_TIME));
        InterviewSession awaitingAnswer = new InterviewSession(
                SessionId.generate(), new SessionState.AwaitingAnswer(), existing);

        InterviewSession result = awaitingAnswer.apply(
                new SessionCommand.SubmitAnswer("I am a backend developer", ANSWER_TIME));

        assertThat(result.state()).isEqualTo(new SessionState.InProgress());
        assertThat(result.transcript().messages()).containsExactly(
                new Message(MessageRole.INTERVIEWER, "Tell me about yourself", QUESTION_TIME),
                new Message(MessageRole.CANDIDATE, "I am a backend developer", ANSWER_TIME));
    }

    @Test
    @DisplayName("ending a session while awaiting an answer transitions it to Completed and keeps the unanswered question")
    void apply_endInterviewOnAwaitingAnswer_transitionsToCompleted() {
        Transcript existing = Transcript.empty()
                .append(new Message(MessageRole.INTERVIEWER, "Tell me about yourself", QUESTION_TIME));
        InterviewSession awaitingAnswer = new InterviewSession(
                SessionId.generate(), new SessionState.AwaitingAnswer(), existing);

        InterviewSession result = awaitingAnswer.apply(new SessionCommand.EndInterview());

        assertThat(result.state()).isEqualTo(new SessionState.Completed());
        assertThat(result.transcript()).isEqualTo(existing);
    }

    @Test
    @DisplayName("cancelling a newly created session transitions it to Cancelled")
    void apply_cancelInterviewOnCreated_transitionsToCancelled() {
        InterviewSession created = InterviewSession.create(SessionId.generate());

        InterviewSession result = created.apply(new SessionCommand.CancelInterview());

        assertThat(result.state()).isEqualTo(new SessionState.Cancelled());
    }

    @Test
    @DisplayName("cancelling an in-progress session transitions it to Cancelled without altering the transcript")
    void apply_cancelInterviewOnInProgress_transitionsToCancelled() {
        Transcript existing = Transcript.empty()
                .append(new Message(MessageRole.INTERVIEWER, "Tell me about yourself", QUESTION_TIME));
        InterviewSession inProgress = new InterviewSession(
                SessionId.generate(), new SessionState.InProgress(), existing);

        InterviewSession result = inProgress.apply(new SessionCommand.CancelInterview());

        assertThat(result.state()).isEqualTo(new SessionState.Cancelled());
        assertThat(result.transcript()).isEqualTo(existing);
    }

    @Test
    @DisplayName("cancelling a session while awaiting an answer transitions it to Cancelled and keeps the unanswered question")
    void apply_cancelInterviewOnAwaitingAnswer_transitionsToCancelled() {
        Transcript existing = Transcript.empty()
                .append(new Message(MessageRole.INTERVIEWER, "Tell me about yourself", QUESTION_TIME));
        InterviewSession awaitingAnswer = new InterviewSession(
                SessionId.generate(), new SessionState.AwaitingAnswer(), existing);

        InterviewSession result = awaitingAnswer.apply(new SessionCommand.CancelInterview());

        assertThat(result.state()).isEqualTo(new SessionState.Cancelled());
        assertThat(result.transcript()).isEqualTo(existing);
    }

    @Test
    @DisplayName("a full interview lifecycle produces a transcript with correctly ordered messages")
    void apply_fullConversationLifecycle_producesCorrectlyOrderedTranscript() {
        InterviewSession session = InterviewSession.create(SessionId.generate())
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME))
                .apply(new SessionCommand.SubmitAnswer("I am a backend developer", ANSWER_TIME))
                .apply(new SessionCommand.AskQuestion("Describe a challenging project", QUESTION_TIME.plusSeconds(60)))
                .apply(new SessionCommand.SubmitAnswer(
                        "I migrated a monolith to microservices", ANSWER_TIME.plusSeconds(60)))
                .apply(new SessionCommand.AskQuestion("What did you learn from that?", QUESTION_TIME.plusSeconds(120)))
                .apply(new SessionCommand.EndInterview());

        assertThat(session.state()).isEqualTo(new SessionState.Completed());
        assertThat(session.transcript().messages()).containsExactly(
                new Message(MessageRole.INTERVIEWER, "Tell me about yourself", QUESTION_TIME),
                new Message(MessageRole.CANDIDATE, "I am a backend developer", ANSWER_TIME),
                new Message(MessageRole.INTERVIEWER, "Describe a challenging project", QUESTION_TIME.plusSeconds(60)),
                new Message(MessageRole.CANDIDATE,
                        "I migrated a monolith to microservices", ANSWER_TIME.plusSeconds(60)),
                new Message(MessageRole.INTERVIEWER, "What did you learn from that?", QUESTION_TIME.plusSeconds(120)));
    }

    @Test
    @DisplayName("every valid transition returns a new instance rather than mutating the original")
    void apply_onValidTransition_returnsNewInstanceNotSameReference() {
        InterviewSession created = InterviewSession.create(SessionId.generate());

        InterviewSession afterStart = created.apply(new SessionCommand.StartInterview());

        assertThat(afterStart).isNotSameAs(created);
    }

    @ParameterizedTest(name = "[{index}] state={0}, command={1} -> {2}")
    @MethodSource("transitionMatrix")
    @DisplayName("every state and command combination transitions to the expected state or throws SessionTransitionException")
    void apply_everyStateAndCommandCombination_transitionsAsExpectedOrThrows(
            SessionState state, SessionCommand command, Class<? extends SessionState> expectedResultState) {
        InterviewSession session = new InterviewSession(SessionId.generate(), state, Transcript.empty());

        if (expectedResultState == null) {
            SessionTransitionException exception = catchThrowableOfType(
                    SessionTransitionException.class, () -> session.apply(command));

            assertThat(exception).isNotNull();
            assertThat(exception.fromState()).isEqualTo(state);
            assertThat(exception.rejectedCommand()).isEqualTo(command);
        } else {
            InterviewSession result = session.apply(command);

            assertThat(result.state()).isInstanceOf(expectedResultState);
        }
    }

    @Test
    @DisplayName("a session is left unchanged after a rejected command")
    void apply_invalidTransition_originalSessionRemainsUnchanged() {
        InterviewSession created = InterviewSession.create(SessionId.generate());

        assertThatThrownBy(() -> created.apply(new SessionCommand.EndInterview()))
                .isInstanceOf(SessionTransitionException.class);

        assertThat(created.state()).isEqualTo(new SessionState.Created());
        assertThat(created.transcript().messages()).isEmpty();
    }

    @Test
    @DisplayName("apply rejects a null command")
    void apply_withNullCommand_throwsNullPointerException() {
        InterviewSession created = InterviewSession.create(SessionId.generate());

        assertThatNullPointerException().isThrownBy(() -> created.apply(null));
    }

    @Test
    @DisplayName("appending a message to the transcript never mutates the previous transcript instance")
    void apply_appendingMessage_doesNotMutatePreviousTranscriptInstance() {
        InterviewSession inProgress = new InterviewSession(
                SessionId.generate(), new SessionState.InProgress(), Transcript.empty());
        Transcript before = inProgress.transcript();

        inProgress.apply(new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME));

        assertThat(before.messages()).isEmpty();
    }

    @Test
    @DisplayName("two sessions with the same id, state and transcript are equal")
    void equals_sessionsWithIdenticalIdStateAndTranscript_areEqual() {
        SessionId id = SessionId.generate();
        Transcript transcript = Transcript.empty();

        InterviewSession first = new InterviewSession(id, new SessionState.InProgress(), transcript);
        InterviewSession second = new InterviewSession(id, new SessionState.InProgress(), transcript);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("two sessions with different ids are not equal even with identical state and transcript")
    void equals_sessionsWithDifferentIdButSameStateAndTranscript_areNotEqual() {
        Transcript transcript = Transcript.empty();

        InterviewSession first = new InterviewSession(SessionId.generate(), new SessionState.InProgress(), transcript);
        InterviewSession second = new InterviewSession(SessionId.generate(), new SessionState.InProgress(), transcript);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("SessionState has exactly the five expected permitted implementations")
    void sessionState_permittedSubclasses_areExactlyFiveExpectedTypes() {
        assertThat(SessionState.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
                SessionState.Created.class,
                SessionState.InProgress.class,
                SessionState.AwaitingAnswer.class,
                SessionState.Completed.class,
                SessionState.Cancelled.class);
    }

    @Test
    @DisplayName("SessionCommand has exactly the five expected permitted implementations")
    void sessionCommand_permittedSubclasses_areExactlyFiveExpectedTypes() {
        assertThat(SessionCommand.class.getPermittedSubclasses()).containsExactlyInAnyOrder(
                SessionCommand.StartInterview.class,
                SessionCommand.AskQuestion.class,
                SessionCommand.SubmitAnswer.class,
                SessionCommand.EndInterview.class,
                SessionCommand.CancelInterview.class);
    }

    /**
     * Every one of the 5 states x 5 commands = 25 combinations, each mapped to either
     * the expected resulting state class (legal transition) or {@code null}, meaning
     * the combination must throw {@link SessionTransitionException}.
     */
    private static Stream<Arguments> transitionMatrix() {
        return Stream.of(
                // Created
                legal(new SessionState.Created(), new SessionCommand.StartInterview(), SessionState.InProgress.class),
                illegal(new SessionState.Created(),
                        new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME)),
                illegal(new SessionState.Created(), new SessionCommand.SubmitAnswer("I am a developer", ANSWER_TIME)),
                illegal(new SessionState.Created(), new SessionCommand.EndInterview()),
                legal(new SessionState.Created(), new SessionCommand.CancelInterview(), SessionState.Cancelled.class),

                // InProgress
                illegal(new SessionState.InProgress(), new SessionCommand.StartInterview()),
                legal(new SessionState.InProgress(),
                        new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME),
                        SessionState.AwaitingAnswer.class),
                illegal(new SessionState.InProgress(),
                        new SessionCommand.SubmitAnswer("I am a developer", ANSWER_TIME)),
                illegal(new SessionState.InProgress(), new SessionCommand.EndInterview()),
                legal(new SessionState.InProgress(), new SessionCommand.CancelInterview(), SessionState.Cancelled.class),

                // AwaitingAnswer
                illegal(new SessionState.AwaitingAnswer(), new SessionCommand.StartInterview()),
                illegal(new SessionState.AwaitingAnswer(),
                        new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME)),
                legal(new SessionState.AwaitingAnswer(),
                        new SessionCommand.SubmitAnswer("I am a developer", ANSWER_TIME),
                        SessionState.InProgress.class),
                legal(new SessionState.AwaitingAnswer(), new SessionCommand.EndInterview(), SessionState.Completed.class),
                legal(new SessionState.AwaitingAnswer(), new SessionCommand.CancelInterview(),
                        SessionState.Cancelled.class),

                // Completed (terminal)
                illegal(new SessionState.Completed(), new SessionCommand.StartInterview()),
                illegal(new SessionState.Completed(),
                        new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME)),
                illegal(new SessionState.Completed(), new SessionCommand.SubmitAnswer("I am a developer", ANSWER_TIME)),
                illegal(new SessionState.Completed(), new SessionCommand.EndInterview()),
                illegal(new SessionState.Completed(), new SessionCommand.CancelInterview()),

                // Cancelled (terminal)
                illegal(new SessionState.Cancelled(), new SessionCommand.StartInterview()),
                illegal(new SessionState.Cancelled(),
                        new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME)),
                illegal(new SessionState.Cancelled(), new SessionCommand.SubmitAnswer("I am a developer", ANSWER_TIME)),
                illegal(new SessionState.Cancelled(), new SessionCommand.EndInterview()),
                illegal(new SessionState.Cancelled(), new SessionCommand.CancelInterview())
        );
    }

    private static Arguments legal(SessionState state, SessionCommand command, Class<? extends SessionState> expected) {
        return Arguments.of(state, command, expected);
    }

    private static Arguments illegal(SessionState state, SessionCommand command) {
        return Arguments.of(state, command, null);
    }
}
