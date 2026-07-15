package com.interviewai.session.application;

import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.interview.domain.QuestionResponse;
import com.interviewai.interview.domain.QuestionResponseStatus;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.session.domain.SessionTransitionException;
import com.interviewai.session.domain.Transcript;
import com.interviewai.shared.InterviewCompletedEvent;
import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID FIXED_EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private QuestionResponseStore questionResponseStore;

    @Mock
    private QuestionGenerationCoordinator questionGenerationCoordinator;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ControllableExecutor controllableExecutor;
    private SessionApplicationService service;

    @BeforeEach
    void setUp() {
        controllableExecutor = new ControllableExecutor();
        Supplier<UUID> eventIdGenerator = () -> FIXED_EVENT_ID;
        service = new SessionApplicationService(
                sessionRepository,
                questionResponseStore,
                questionGenerationCoordinator,
                transactionTemplate,
                controllableExecutor,
                eventPublisher,
                eventIdGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName("starting an interview returns a response handle without waiting for generation")
    void startInterview_returnsHandleBeforeGenerationRuns() {
        ResponseId responseId = ResponseId.generate();
        when(questionResponseStore.createPending(any())).thenAnswer(invocation -> {
            SessionId sessionId = invocation.getArgument(0);
            return pendingResponse(sessionId, responseId);
        });

        AcceptedGeneration accepted = service.startInterview(Optional.empty());

        assertThat(accepted.sessionId()).isNotNull();
        assertThat(accepted.responseId()).isEqualTo(responseId);
        assertThat(controllableExecutor.hasPending()).isTrue();
        verify(questionGenerationCoordinator, never()).generate(any());
    }

    @Test
    @DisplayName("persisted command state exists before worker execution")
    void startInterview_persistsSessionAndPendingResponseBeforeWorkerRuns() {
        when(questionResponseStore.createPending(any())).thenAnswer(invocation -> {
            SessionId sessionId = invocation.getArgument(0);
            return pendingResponse(sessionId, ResponseId.generate());
        });

        service.startInterview(Optional.empty());

        InOrder order = inOrder(sessionRepository, questionResponseStore);
        order.verify(sessionRepository).save(any());
        order.verify(questionResponseStore).createPending(any());
        assertThat(controllableExecutor.hasPending()).isTrue();
    }

    @Test
    @DisplayName("worker execution is submitted only after the acceptance transaction")
    void startInterview_submitsWorkerAfterTransaction() {
        when(questionResponseStore.createPending(any())).thenReturn(
                pendingResponse(SessionId.generate(), ResponseId.generate()));

        AcceptedGeneration accepted = service.startInterview(Optional.empty());
        controllableExecutor.runNext();

        verify(questionGenerationCoordinator).generate(accepted);
    }

    @Test
    @DisplayName("submitting an answer returns a response handle without waiting for generation")
    void submitAnswer_returnsHandleBeforeGenerationRuns() {
        SessionId id = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionRepository.findById(id)).thenReturn(Optional.of(awaitingAnswer));
        when(questionResponseStore.createPending(id)).thenReturn(pendingResponse(id, responseId));

        AcceptedGeneration accepted = service.submitAnswer(id, "I am a backend developer.");

        assertThat(accepted.sessionId()).isEqualTo(id);
        assertThat(accepted.responseId()).isEqualTo(responseId);
        assertThat(controllableExecutor.hasPending()).isTrue();
        verify(questionGenerationCoordinator, never()).generate(any());
    }

    @Test
    @DisplayName("submitting an answer persists the candidate message before worker execution")
    void submitAnswer_persistsCandidateMessageBeforeWorkerRuns() {
        SessionId id = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionRepository.findById(id)).thenReturn(Optional.of(awaitingAnswer));
        when(questionResponseStore.createPending(id)).thenReturn(pendingResponse(id, ResponseId.generate()));

        service.submitAnswer(id, "I am a backend developer.");

        verify(sessionRepository).save(org.mockito.ArgumentMatchers.argThat(session ->
                session.state().equals(new SessionState.InProgress())
                        && session.transcript().messages().stream()
                        .anyMatch(message -> message.role() == MessageRole.CANDIDATE
                                && message.content().equals("I am a backend developer."))));
    }

    @Test
    @DisplayName("submitting an answer for an unknown session throws SessionNotFoundException")
    void submitAnswer_withUnknownSession_throwsSessionNotFoundException() {
        SessionId id = SessionId.generate();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitAnswer(id, "an answer"))
                .isInstanceOf(SessionNotFoundException.class);
        verify(questionResponseStore, never()).createPending(any());
    }

    @Test
    @DisplayName("submitting an answer when the session is not awaiting one throws SessionTransitionException")
    void submitAnswer_whenSessionNotAwaitingAnswer_throwsSessionTransitionException() {
        SessionId id = SessionId.generate();
        InterviewSession created = InterviewSession.create(id);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(created));

        assertThatThrownBy(() -> service.submitAnswer(id, "an answer"))
                .isInstanceOf(SessionTransitionException.class);
        verify(sessionRepository, never()).save(any());
        verify(questionResponseStore, never()).createPending(any());
    }

    @Test
    @DisplayName("getSession returns the persisted session")
    void getSession_returnsPersistedSession() {
        SessionId id = SessionId.generate();
        InterviewSession session = new InterviewSession(id, null, new SessionState.InProgress(), Transcript.empty());
        when(sessionRepository.findById(id)).thenReturn(Optional.of(session));

        assertThat(service.getSession(id)).isEqualTo(session);
    }

    @Test
    @DisplayName("getSession for an unknown session throws SessionNotFoundException")
    void getSession_withUnknownSession_throwsSessionNotFoundException() {
        SessionId id = SessionId.generate();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSession(id)).isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    @DisplayName("endInterview publishes one InterviewCompletedEvent with deterministic identifiers")
    void endInterview_whenAwaitingAnswer_publishesDeterministicCompletionEvent() {
        SessionId id = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionRepository.findById(id)).thenReturn(Optional.of(awaitingAnswer));

        InterviewSession result = service.endInterview(id);

        assertThat(result.state()).isEqualTo(new SessionState.Completed());
        ArgumentCaptor<InterviewCompletedEvent> eventCaptor = ArgumentCaptor.forClass(InterviewCompletedEvent.class);
        InOrder order = inOrder(sessionRepository, eventPublisher);
        order.verify(sessionRepository).save(result);
        order.verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new InterviewCompletedEvent(FIXED_EVENT_ID, id, NOW));
    }

    @Test
    @DisplayName("endInterview for an unknown session throws SessionNotFoundException")
    void endInterview_withUnknownSession_throwsSessionNotFoundException() {
        SessionId id = SessionId.generate();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.endInterview(id)).isInstanceOf(SessionNotFoundException.class);
        verify(sessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("endInterview when the session is not awaiting an answer throws and publishes no event")
    void endInterview_whenNotAwaitingAnswer_throwsSessionTransitionException() {
        SessionId id = SessionId.generate();
        InterviewSession created = InterviewSession.create(id);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(created));

        assertThatThrownBy(() -> service.endInterview(id)).isInstanceOf(SessionTransitionException.class);
        verify(sessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("endInterview after the session is already completed publishes no additional event")
    void endInterview_whenAlreadyCompleted_throwsAndPublishesNoEvent() {
        SessionId id = SessionId.generate();
        InterviewSession completed = new InterviewSession(id, null, new SessionState.Completed(), Transcript.empty());
        when(sessionRepository.findById(id)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.endInterview(id)).isInstanceOf(SessionTransitionException.class);
        verify(sessionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("endInterview publishes no event when persistence fails")
    void endInterview_whenPersistenceFails_publishesNoEvent() {
        SessionId id = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionRepository.findById(id)).thenReturn(Optional.of(awaitingAnswer));
        doThrow(new IllegalStateException("database unavailable")).when(sessionRepository).save(any());

        assertThatThrownBy(() -> service.endInterview(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("cancelInterview transitions a newly created session to Cancelled and cancels active response")
    void cancelInterview_whenCreated_transitionsToCancelledAndCancelsActiveResponse() {
        SessionId id = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession created = InterviewSession.create(id);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(created));
        when(questionResponseStore.findActiveBySessionId(id)).thenReturn(Optional.of(pendingResponse(id, responseId)));

        InterviewSession result = service.cancelInterview(id);

        assertThat(result.state()).isEqualTo(new SessionState.Cancelled());
        verify(sessionRepository).save(result);
        verify(questionResponseStore).markCancelled(responseId);
    }

    @Test
    @DisplayName("cancelInterview for an unknown session throws SessionNotFoundException")
    void cancelInterview_withUnknownSession_throwsSessionNotFoundException() {
        SessionId id = SessionId.generate();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelInterview(id)).isInstanceOf(SessionNotFoundException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelInterview on an already completed session throws SessionTransitionException")
    void cancelInterview_whenAlreadyCompleted_throwsSessionTransitionException() {
        SessionId id = SessionId.generate();
        InterviewSession completed = new InterviewSession(id, null, new SessionState.Completed(), Transcript.empty());
        when(sessionRepository.findById(id)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.cancelInterview(id)).isInstanceOf(SessionTransitionException.class);
        verify(sessionRepository, never()).save(any());
    }

    private QuestionResponse pendingResponse(SessionId sessionId, ResponseId responseId) {
        return new QuestionResponse(
                responseId,
                sessionId,
                QuestionResponseStatus.PENDING,
                "",
                null,
                0,
                null,
                NOW,
                NOW);
    }
}
