package com.interviewai.session.application;

import com.interviewai.cv.application.CvRetrievalService;
import com.interviewai.interview.application.QuestionGenerationException;
import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.interview.application.port.out.StreamingQuestionGenerator;
import com.interviewai.interview.domain.QuestionResponse;
import com.interviewai.interview.domain.QuestionResponseStatus;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.session.domain.SessionTransitionException;
import com.interviewai.session.domain.Transcript;
import com.interviewai.shared.CvId;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuestionGenerationCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private QuestionResponseStore questionResponseStore;

    @Mock
    private StreamingQuestionGenerator streamingQuestionGenerator;

    @Mock
    private CvRetrievalService cvRetrievalService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private TransactionTemplate transactionTemplate;
    private QuestionGenerationCoordinator coordinator;

    @BeforeEach
    void setUp() throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        transactionTemplate = new TransactionTemplate(transactionManager);
        coordinator = new QuestionGenerationCoordinator(
                sessionRepository,
                questionResponseStore,
                streamingQuestionGenerator,
                cvRetrievalService,
                transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("partial responses are stored in order during generation")
    void generate_storesPartialsInOrder() {
        SessionId sessionId = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession session = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(activeResponse(responseId, sessionId)));
        when(streamingQuestionGenerator.generateNextQuestion(any(), any(), any())).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(2);
            consumer.accept("Tell ");
            consumer.accept("me more.");
            return "Tell me more.";
        });

        coordinator.generate(new AcceptedGeneration(sessionId, responseId));

        InOrder order = inOrder(questionResponseStore);
        order.verify(questionResponseStore).markStreaming(responseId);
        order.verify(questionResponseStore).appendTokenEvent(responseId, "Tell ");
        order.verify(questionResponseStore).appendTokenEvent(responseId, "me more.");
        order.verify(questionResponseStore).markCompleted(responseId, "Tell me more.");
    }

    @Test
    @DisplayName("successful completion appends one interviewer message and transitions to AwaitingAnswer")
    void generate_onSuccess_appendsOneInterviewerMessage() {
        SessionId sessionId = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession session = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(activeResponse(responseId, sessionId)));
        when(streamingQuestionGenerator.generateNextQuestion(any(), any(), any())).thenReturn("Tell me about yourself.");

        coordinator.generate(new AcceptedGeneration(sessionId, responseId));

        ArgumentCaptor<InterviewSession> saved = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionRepository).save(saved.capture());
        assertThat(saved.getValue().state()).isEqualTo(new SessionState.AwaitingAnswer());
        assertThat(saved.getValue().transcript().messages()).hasSize(1);
        assertThat(saved.getValue().transcript().messages().getFirst().content()).isEqualTo("Tell me about yourself.");
    }

    @Test
    @DisplayName("provider failure persists an error and leaves transcript without an interviewer message")
    void generate_onProviderFailure_persistsErrorWithoutInterviewerMessage() {
        SessionId sessionId = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession session = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(activeResponse(responseId, sessionId)));
        when(streamingQuestionGenerator.generateNextQuestion(any(), any(), any()))
                .thenThrow(new QuestionGenerationException("provider failed"));

        coordinator.generate(new AcceptedGeneration(sessionId, responseId));

        verify(questionResponseStore).markFailed(responseId, QuestionGenerationCoordinator.SAFE_FAILURE_MESSAGE);
        verify(sessionRepository, never()).save(any());
        verify(questionResponseStore, never()).markCompleted(any(), anyString());
    }

    @Test
    @DisplayName("cancellation during generation does not append a question")
    void generate_whenSessionCancelled_doesNotAppendQuestion() {
        SessionId sessionId = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession cancelled = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.CancelInterview());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(cancelled));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(activeResponse(responseId, sessionId)));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(activeResponse(responseId, sessionId)));

        coordinator.generate(new AcceptedGeneration(sessionId, responseId));

        verify(questionResponseStore).markCancelled(responseId);
        verify(streamingQuestionGenerator, never()).generateNextQuestion(any(), any(), any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("terminal response cannot complete twice")
    void generate_whenResponseAlreadyTerminal_doesNotAppendQuestionAgain() {
        SessionId sessionId = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession session = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview());
        QuestionResponse completed = new QuestionResponse(
                responseId,
                sessionId,
                QuestionResponseStatus.COMPLETED,
                "Tell me more.",
                "Tell me more.",
                2,
                null,
                NOW,
                NOW);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(completed));
        when(streamingQuestionGenerator.generateNextQuestion(any(), any(), any())).thenReturn("Tell me more.");

        coordinator.generate(new AcceptedGeneration(sessionId, responseId));

        verify(sessionRepository, never()).save(any());
        verify(questionResponseStore, never()).markCompleted(any(), anyString());
    }

    @Test
    @DisplayName("first question uses job offer as retrieval query")
    void generate_firstQuestion_usesJobOfferForRetrieval() {
        CvId cvId = new CvId(UUID.randomUUID());
        SessionId sessionId = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession session = InterviewSession.create(sessionId, cvId)
                .apply(new SessionCommand.StartInterview());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(activeResponse(responseId, sessionId)));
        when(cvRetrievalService.retrieveJobOffer(cvId)).thenReturn("Java + Spring Boot");
        when(cvRetrievalService.retrieveContext(cvId, "Java + Spring Boot", 4))
                .thenReturn(new CvRetrievalService.CvContext("Java + Spring Boot", List.of("Allegro project")));
        when(streamingQuestionGenerator.generateNextQuestion(any(), any(), any())).thenReturn("Tell me about Allegro.");

        coordinator.generate(new AcceptedGeneration(sessionId, responseId));

        verify(cvRetrievalService).retrieveJobOffer(cvId);
        verify(cvRetrievalService).retrieveContext(cvId, "Java + Spring Boot", 4);
        verify(streamingQuestionGenerator).generateNextQuestion(any(), argThat(context ->
                context.jobOffer().equals("Java + Spring Boot")
                        && context.cvExcerpts().contains("Allegro project")), any());
    }

    @Test
    @DisplayName("follow-up uses the last candidate answer for retrieval")
    void generate_followUp_usesLastCandidateAnswerForRetrieval() {
        CvId cvId = new CvId(UUID.randomUUID());
        SessionId sessionId = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession session = InterviewSession.create(sessionId, cvId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW))
                .apply(new SessionCommand.SubmitAnswer("I worked with Kafka at Allegro.", NOW));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(activeResponse(responseId, sessionId)));
        when(cvRetrievalService.retrieveContext(cvId, "I worked with Kafka at Allegro.", 4))
                .thenReturn(new CvRetrievalService.CvContext("Backend role", List.of("Kafka", "Allegro")));
        when(streamingQuestionGenerator.generateNextQuestion(any(), any(), any())).thenReturn("How did you scale Kafka?");

        coordinator.generate(new AcceptedGeneration(sessionId, responseId));

        verify(cvRetrievalService).retrieveContext(cvId, "I worked with Kafka at Allegro.", 4);
    }

    @Test
    @DisplayName("cancelled session at completion time does not append a question")
    void generate_whenSessionCancelledBeforeFinalize_doesNotAppendQuestion() {
        SessionId sessionId = SessionId.generate();
        ResponseId responseId = ResponseId.generate();
        InterviewSession inProgress = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview());
        InterviewSession cancelled = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.CancelInterview());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(inProgress), Optional.of(cancelled));
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(activeResponse(responseId, sessionId)));
        when(streamingQuestionGenerator.generateNextQuestion(any(), any(), any())).thenReturn("Too late.");

        coordinator.generate(new AcceptedGeneration(sessionId, responseId));

        verify(questionResponseStore).markCancelled(responseId);
        verify(sessionRepository, never()).save(any());
        verify(questionResponseStore, never()).markCompleted(any(), anyString());
    }

    private QuestionResponse activeResponse(ResponseId responseId, SessionId sessionId) {
        return new QuestionResponse(
                responseId,
                sessionId,
                QuestionResponseStatus.STREAMING,
                "",
                null,
                0,
                null,
                NOW,
                NOW);
    }
}
