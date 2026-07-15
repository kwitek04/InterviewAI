package com.interviewai.session.application;

import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.shared.InterviewCompletedEvent;
import com.interviewai.shared.SessionId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Import(InterviewCompletedEventPublicationIT.TestListeners.class)
class InterviewCompletedEventPublicationIT {

    private static final Instant QUESTION_TIME = Instant.parse("2026-01-01T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SessionApplicationService sessionApplicationService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private RecordingCompletionListener recordingCompletionListener;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("ending an interview writes a durable Event Publication Registry row")
    void endInterview_persistsOutboxPublication() {
        SessionId sessionId = persistAwaitingAnswerSession();

        sessionApplicationService.endInterview(sessionId);
        flushAndClear();

        List<Map<String, Object>> publications = jdbcClient.sql("""
                        SELECT event_type, serialized_event, completion_date, status
                        FROM event_publication
                        WHERE serialized_event LIKE :sessionMarker
                        """)
                .param("sessionMarker", "%" + sessionId.value() + "%")
                .query()
                .listOfRows();

        assertThat(publications).hasSize(1);
        Map<String, Object> publication = publications.getFirst();
        assertThat(publication.get("event_type")).isEqualTo(InterviewCompletedEvent.class.getName());
        assertThat((String) publication.get("serialized_event")).contains(sessionId.value().toString());
        assertThat(sessionRepository.findById(sessionId)).get()
                .extracting(InterviewSession::state)
                .isEqualTo(new SessionState.Completed());
        assertThat(recordingCompletionListener.events())
                .extracting(InterviewCompletedEvent::sessionId)
                .contains(sessionId);
    }

    @Test
    @DisplayName("a failed surrounding transaction rolls back both COMPLETED state and the outbox publication")
    void endInterview_rollsBackSessionAndOutboxTogether() {
        SessionId sessionId = persistAwaitingAnswerSession();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            sessionApplicationService.endInterview(sessionId);
            throw new IllegalStateException("forced rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("forced rollback");

        flushAndClear();

        assertThat(sessionRepository.findById(sessionId)).get()
                .extracting(InterviewSession::state)
                .isEqualTo(new SessionState.AwaitingAnswer());
        Integer publicationCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM event_publication
                        WHERE serialized_event LIKE :sessionMarker
                        """)
                .param("sessionMarker", "%" + sessionId.value() + "%")
                .query(Integer.class)
                .single();
        assertThat(publicationCount).isZero();
        assertThat(recordingCompletionListener.events())
                .extracting(InterviewCompletedEvent::sessionId)
                .doesNotContain(sessionId);
    }

    private SessionId persistAwaitingAnswerSession() {
        SessionId sessionId = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", QUESTION_TIME));
        sessionRepository.save(awaitingAnswer);
        flushAndClear();
        return sessionId;
    }

    private void flushAndClear() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.flush();
            entityManager.clear();
        });
    }

    @TestConfiguration
    static class TestListeners {

        @Bean
        RecordingCompletionListener recordingCompletionListener() {
            return new RecordingCompletionListener();
        }
    }

    static class RecordingCompletionListener {

        private final List<InterviewCompletedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void onInterviewCompleted(InterviewCompletedEvent event) {
            events.add(event);
        }

        List<InterviewCompletedEvent> events() {
            return List.copyOf(events);
        }
    }
}
