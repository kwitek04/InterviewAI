package com.interviewai.session.application;

import com.interviewai.cv.application.CvRetrievalService;
import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.interview.application.port.out.QuestionGenerator;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.session.domain.SessionTransitionException;
import com.interviewai.session.domain.Transcript;
import com.interviewai.shared.CvId;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private QuestionGenerator questionGenerator;

    @Mock
    private CvRetrievalService cvRetrievalService;

    private SessionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SessionApplicationService(
                sessionRepository, questionGenerator, cvRetrievalService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("starting an interview asks the first question and persists the session in AwaitingAnswer")
    void startInterview_asksFirstQuestionAndPersists() {
        when(questionGenerator.generateNextQuestion(any(), any())).thenReturn("Tell me about yourself.");

        InterviewSession session = service.startInterview(Optional.empty());

        assertThat(session.state()).isEqualTo(new SessionState.AwaitingAnswer());
        assertThat(session.transcript().messages()).hasSize(1);
        assertThat(session.transcript().messages().getFirst().role()).isEqualTo(MessageRole.INTERVIEWER);
        assertThat(session.transcript().messages().getFirst().content()).isEqualTo("Tell me about yourself.");
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("submitting an answer appends it, asks the next question, and persists the result")
    void submitAnswer_appendsAnswerAndAsksNextQuestion() {
        SessionId id = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionRepository.findById(id)).thenReturn(Optional.of(awaitingAnswer));
        when(questionGenerator.generateNextQuestion(any(), any())).thenReturn("What is your experience with Spring Boot?");

        InterviewSession updated = service.submitAnswer(id, "I am a backend developer.");

        assertThat(updated.state()).isEqualTo(new SessionState.AwaitingAnswer());
        assertThat(updated.transcript().messages()).hasSize(3);
        assertThat(updated.transcript().messages().get(1).role()).isEqualTo(MessageRole.CANDIDATE);
        assertThat(updated.transcript().messages().get(1).content()).isEqualTo("I am a backend developer.");
        assertThat(updated.transcript().messages().getLast().content())
                .isEqualTo("What is your experience with Spring Boot?");
        verify(sessionRepository).save(updated);
    }

    @Test
    @DisplayName("submitting an answer for an unknown session throws SessionNotFoundException")
    void submitAnswer_withUnknownSession_throwsSessionNotFoundException() {
        SessionId id = SessionId.generate();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitAnswer(id, "an answer"))
                .isInstanceOf(SessionNotFoundException.class);
        verify(questionGenerator, never()).generateNextQuestion(any(), any());
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
    @DisplayName("endInterview transitions an awaiting-answer session to Completed and persists it")
    void endInterview_whenAwaitingAnswer_transitionsToCompletedAndPersists() {
        SessionId id = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionRepository.findById(id)).thenReturn(Optional.of(awaitingAnswer));

        InterviewSession result = service.endInterview(id);

        assertThat(result.state()).isEqualTo(new SessionState.Completed());
        verify(sessionRepository).save(result);
        verify(questionGenerator, never()).generateNextQuestion(any(), any());
    }

    @Test
    @DisplayName("endInterview for an unknown session throws SessionNotFoundException")
    void endInterview_withUnknownSession_throwsSessionNotFoundException() {
        SessionId id = SessionId.generate();
        when(sessionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.endInterview(id)).isInstanceOf(SessionNotFoundException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("endInterview when the session is not awaiting an answer throws SessionTransitionException")
    void endInterview_whenNotAwaitingAnswer_throwsSessionTransitionException() {
        SessionId id = SessionId.generate();
        InterviewSession created = InterviewSession.create(id);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(created));

        assertThatThrownBy(() -> service.endInterview(id)).isInstanceOf(SessionTransitionException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelInterview transitions a newly created session to Cancelled and persists it")
    void cancelInterview_whenCreated_transitionsToCancelledAndPersists() {
        SessionId id = SessionId.generate();
        InterviewSession created = InterviewSession.create(id);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(created));

        InterviewSession result = service.cancelInterview(id);

        assertThat(result.state()).isEqualTo(new SessionState.Cancelled());
        verify(sessionRepository).save(result);
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

    @Test
    @DisplayName("starting with cvId retrieves context using job offer on first question")
    void startInterview_withCvId_retrievesContextWithJobOffer() {
        CvId cvId = new CvId(UUID.randomUUID());
        when(cvRetrievalService.retrieveJobOffer(cvId)).thenReturn("Java + Spring Boot");
        when(cvRetrievalService.retrieveContext(cvId, "Java + Spring Boot", 4))
                .thenReturn(new CvRetrievalService.CvContext("Java + Spring Boot", List.of("Allegro project")));
        when(questionGenerator.generateNextQuestion(any(), any())).thenReturn("Tell me about Allegro.");

        InterviewSession session = service.startInterview(Optional.of(cvId));

        assertThat(session.cvId()).contains(cvId);
        verify(cvRetrievalService).retrieveJobOffer(cvId);
        verify(cvRetrievalService).retrieveContext(cvId, "Java + Spring Boot", 4);
        verify(questionGenerator).generateNextQuestion(any(), argThat(context ->
                context.jobOffer().equals("Java + Spring Boot") && context.cvExcerpts().contains("Allegro project")));
    }

    @Test
    @DisplayName("follow-up with cvId retrieves context using the last candidate answer")
    void submitAnswer_withCvId_retrievesContextWithLastAnswer() {
        CvId cvId = new CvId(UUID.randomUUID());
        SessionId id = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(id, cvId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionRepository.findById(id)).thenReturn(Optional.of(awaitingAnswer));
        when(cvRetrievalService.retrieveContext(cvId, "I worked with Kafka at Allegro.", 4))
                .thenReturn(new CvRetrievalService.CvContext("Backend role", List.of("Kafka", "Allegro")));
        when(questionGenerator.generateNextQuestion(any(), any())).thenReturn("How did you scale Kafka?");

        service.submitAnswer(id, "I worked with Kafka at Allegro.");

        verify(cvRetrievalService).retrieveContext(cvId, "I worked with Kafka at Allegro.", 4);
    }

    @Test
    @DisplayName("without cvId passes empty interview context and never calls retrieval")
    void startInterview_withoutCvId_usesEmptyContext() {
        when(questionGenerator.generateNextQuestion(any(), any())).thenReturn("Tell me about yourself.");

        service.startInterview(Optional.empty());

        verify(cvRetrievalService, never()).retrieveContext(any(), anyString(), anyInt());
        verify(questionGenerator).generateNextQuestion(any(), eq(InterviewContext.empty()));
    }
}
