package com.interviewai.interview.adapter.out.persistence;

import com.interviewai.interview.application.ActiveQuestionResponseAlreadyExistsException;
import com.interviewai.interview.application.QuestionResponseNotFoundException;
import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.interview.domain.QuestionResponse;
import com.interviewai.interview.domain.QuestionResponseEventType;
import com.interviewai.interview.domain.QuestionResponseStatus;
import com.interviewai.interview.domain.QuestionResponseStreamEvent;
import com.interviewai.shared.SessionId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(QuestionResponsePersistenceAdapter.class)
class QuestionResponsePersistenceAdapterIT {

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
    private QuestionResponseStore store;

    @Autowired
    private JdbcClient jdbcClient;

    @PersistenceContext
    private EntityManager entityManager;

    private SessionId sessionId;

    @BeforeEach
    void setUp() {
        sessionId = SessionId.generate();
        jdbcClient.sql("INSERT INTO interview_session (id, state) VALUES (?, ?)")
                .params(sessionId.value(), "IN_PROGRESS")
                .update();
    }

    @Test
    @DisplayName("created response round-trips with its session ownership")
    void createPending_thenFindById_roundTripsSessionOwnership() {
        QuestionResponse created = store.createPending(sessionId);
        flushAndClear();

        QuestionResponse reloaded = store.findById(created.id()).orElseThrow();
        assertThat(reloaded.sessionId()).isEqualTo(sessionId);
        assertThat(reloaded.status()).isEqualTo(QuestionResponseStatus.PENDING);
        assertThat(store.requireOwnedBySession(created.id(), sessionId)).isEqualTo(reloaded);
    }

    @Test
    @DisplayName("wrong session ownership is rejected when loading a response")
    void requireOwnedBySession_wrongSession_throws() {
        QuestionResponse created = store.createPending(sessionId);
        flushAndClear();

        assertThatThrownBy(() -> store.requireOwnedBySession(created.id(), SessionId.generate()))
                .isInstanceOf(QuestionResponseNotFoundException.class);
    }

    @Test
    @DisplayName("three appended events receive sequences 1, 2, 3")
    void appendTokenEvents_assignsMonotonicSequences() {
        QuestionResponse created = store.createPending(sessionId);
        store.markStreaming(created.id());

        QuestionResponseStreamEvent first = store.appendTokenEvent(created.id(), "How");
        QuestionResponseStreamEvent second = store.appendTokenEvent(created.id(), " did");
        QuestionResponseStreamEvent third = store.appendTokenEvent(created.id(), " you");

        assertThat(List.of(first.sequence(), second.sequence(), third.sequence()))
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("eventsAfter returns only later events in ascending order")
    void eventsAfter_returnsOnlyLaterEventsInOrder() {
        QuestionResponse created = store.createPending(sessionId);
        store.markStreaming(created.id());
        store.appendTokenEvent(created.id(), "one");
        store.appendTokenEvent(created.id(), "two");
        store.appendTokenEvent(created.id(), "three");
        flushAndClear();

        List<QuestionResponseStreamEvent> replayed = store.eventsAfter(created.id(), 1);

        assertThat(replayed).extracting(QuestionResponseStreamEvent::sequence)
                .containsExactly(2, 3);
        assertThat(replayed).extracting(QuestionResponseStreamEvent::payload)
                .containsExactly("two", "three");
    }

    @Test
    @DisplayName("duplicate active response for one session is rejected")
    void createPending_twiceForSameSession_isRejected() {
        store.createPending(sessionId);
        flushAndClear();

        assertThatThrownBy(() -> store.createPending(sessionId))
                .isInstanceOf(ActiveQuestionResponseAlreadyExistsException.class);
    }

    @Test
    @DisplayName("terminal metadata and final text survive reload")
    void markCompleted_thenReload_preservesTerminalMetadata() {
        QuestionResponse created = store.createPending(sessionId);
        store.markStreaming(created.id());
        store.appendTokenEvent(created.id(), "Tell me about ");
        store.markCompleted(created.id(), "Tell me about your Java experience");
        flushAndClear();

        QuestionResponse reloaded = store.findById(created.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(QuestionResponseStatus.COMPLETED);
        assertThat(reloaded.finalText()).isEqualTo("Tell me about your Java experience");
        assertThat(reloaded.accumulatedText()).isEqualTo("Tell me about ");
        assertThat(reloaded.lastEventSequence()).isEqualTo(2);

        List<QuestionResponseStreamEvent> events = store.eventsAfter(created.id(), 0);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).type()).isEqualTo(QuestionResponseEventType.TOKEN);
        assertThat(events.get(1).type()).isEqualTo(QuestionResponseEventType.COMPLETED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("concurrent appends never produce duplicate or missing sequence numbers")
    void concurrentAppendTokenEvents_produceUniqueContiguousSequences() throws Exception {
        QuestionResponse created = store.createPending(sessionId);
        store.markStreaming(created.id());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<Integer> sequences = new ConcurrentLinkedQueue<>();
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            IntStream.range(0, threadCount).forEach(index -> executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(5, TimeUnit.SECONDS);
                    QuestionResponseStreamEvent event = store.appendTokenEvent(created.id(), "x");
                    sequences.add(event.sequence());
                } catch (Throwable throwable) {
                    failures.add(throwable);
                }
            }));

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(failures).isEmpty();
        assertThat(sequences).hasSize(threadCount);
        assertThat(sequences).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, threadCount).boxed().toList());

        QuestionResponse reloaded = store.findById(created.id()).orElseThrow();
        assertThat(reloaded.lastEventSequence()).isEqualTo(threadCount);
        assertThat(reloaded.accumulatedText()).isEqualTo("x".repeat(threadCount));
        assertThat(store.eventsAfter(created.id(), 0)).hasSize(threadCount);
    }

    @Test
    @DisplayName("active response lookup returns only pending or streaming responses")
    void findActiveBySessionId_returnsOnlyActiveResponse() {
        QuestionResponse created = store.createPending(sessionId);
        flushAndClear();

        assertThat(store.findActiveBySessionId(sessionId)).map(QuestionResponse::id)
                .contains(created.id());

        store.markStreaming(created.id());
        store.markCompleted(created.id(), "Done");
        flushAndClear();

        assertThat(store.findActiveBySessionId(sessionId)).isEmpty();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
