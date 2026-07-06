package com.interviewai.session.application;

import com.interviewai.interview.application.port.out.QuestionGenerator;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.session.domain.SessionTransitionException;
import com.interviewai.session.domain.Transcript;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private SessionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SessionApplicationService(sessionRepository, questionGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("starting an interview asks the first question and persists the session in AwaitingAnswer")
    void startInterview_asksFirstQuestionAndPersists() {
        when(questionGenerator.generateNextQuestion(any())).thenReturn("Tell me about yourself.");

        InterviewSession session = service.startInterview();

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
        when(questionGenerator.generateNextQuestion(any())).thenReturn("What is your experience with Spring Boot?");

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
        verify(questionGenerator, never()).generateNextQuestion(any());
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
        InterviewSession session = new InterviewSession(id, new SessionState.InProgress(), Transcript.empty());
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
}
