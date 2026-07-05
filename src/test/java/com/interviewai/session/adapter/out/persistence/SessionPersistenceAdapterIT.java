package com.interviewai.session.adapter.out.persistence;

import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.shared.SessionId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(SessionPersistenceAdapter.class)
class SessionPersistenceAdapterIT {

    private static final Instant QUESTION_TIME = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant ANSWER_TIME = Instant.parse("2026-01-01T10:01:00Z");

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
    private SessionPersistenceAdapter adapter;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("a completed session with its full transcript round-trips back to an identical domain aggregate")
    void save_thenFindById_reconstructsIdenticalAggregate() {
        SessionId id = SessionId.generate();
        InterviewSession session = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself", QUESTION_TIME))
                .apply(new SessionCommand.SubmitAnswer("I am a backend developer", ANSWER_TIME))
                .apply(new SessionCommand.EndInterview());

        adapter.save(session);
        flushAndClear();

        InterviewSession reloaded = adapter.findById(id).orElseThrow();
        assertThat(reloaded).isEqualTo(session);
    }

    @Test
    @DisplayName("the transcript is reloaded with its messages in the original insertion order")
    void save_thenFindById_preservesMessageOrder() {
        SessionId id = SessionId.generate();
        InterviewSession session = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("First question", QUESTION_TIME))
                .apply(new SessionCommand.SubmitAnswer("First answer", ANSWER_TIME))
                .apply(new SessionCommand.AskQuestion("Second question", QUESTION_TIME.plusSeconds(120)))
                .apply(new SessionCommand.SubmitAnswer("Second answer", ANSWER_TIME.plusSeconds(120)));

        adapter.save(session);
        flushAndClear();

        InterviewSession reloaded = adapter.findById(id).orElseThrow();
        assertThat(reloaded.transcript().messages())
                .extracting(message -> message.role().name(), message -> message.content())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(MessageRole.INTERVIEWER.name(), "First question"),
                        org.assertj.core.groups.Tuple.tuple(MessageRole.CANDIDATE.name(), "First answer"),
                        org.assertj.core.groups.Tuple.tuple(MessageRole.INTERVIEWER.name(), "Second question"),
                        org.assertj.core.groups.Tuple.tuple(MessageRole.CANDIDATE.name(), "Second answer"));
    }

    @Test
    @DisplayName("re-saving an appended session inserts only the new messages")
    void save_calledTwiceWithAppendedMessages_doesNotDuplicateExistingMessages() {
        SessionId id = SessionId.generate();
        InterviewSession afterFirstQuestion = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("First question", QUESTION_TIME));
        adapter.save(afterFirstQuestion);
        flushAndClear();

        InterviewSession afterAnswer = afterFirstQuestion
                .apply(new SessionCommand.SubmitAnswer("First answer", ANSWER_TIME));
        adapter.save(afterAnswer);
        flushAndClear();

        InterviewSession reloaded = adapter.findById(id).orElseThrow();
        assertThat(reloaded.transcript().messages()).hasSize(2);
        assertThat(reloaded.state()).isEqualTo(new SessionState.InProgress());
    }

    @Test
    @DisplayName("looking up an unknown session returns an empty result")
    void findById_unknownId_returnsEmpty() {
        assertThat(adapter.findById(SessionId.generate())).isEmpty();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
