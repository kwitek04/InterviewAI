package com.interviewai.report.adapter.out.persistence;

import com.interviewai.report.application.port.out.ProcessedEventStore;
import com.interviewai.report.application.port.out.ProcessedEventStore.ClaimResult;
import com.interviewai.shared.SessionId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ProcessedEventPersistenceAdapter.class)
class ProcessedEventPersistenceAdapterIT {

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
    private ProcessedEventStore processedEventStore;

    @Autowired
    private JdbcClient jdbcClient;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("first claim is acquired and a concurrent claim stays busy until completion")
    void tryClaim_concurrentAndCompletedSemantics() {
        SessionId sessionId = persistCompletedSession();
        UUID eventId = UUID.randomUUID();
        Duration lease = Duration.ofSeconds(30);

        ClaimResult first = processedEventStore.tryClaim(eventId, sessionId, "worker-a", lease);
        ClaimResult second = processedEventStore.tryClaim(eventId, sessionId, "worker-b", lease);

        assertThat(first).isEqualTo(new ClaimResult.Acquired(1));
        assertThat(second).isInstanceOf(ClaimResult.Busy.class);

        processedEventStore.markCompleted(eventId, "worker-a");
        assertThat(processedEventStore.isCompleted(eventId)).isTrue();
        assertThat(processedEventStore.tryClaim(eventId, sessionId, "worker-b", lease))
                .isInstanceOf(ClaimResult.AlreadyCompleted.class);
    }

    @Test
    @DisplayName("a stale PROCESSING claim can be reclaimed after the lease expires")
    void tryClaim_afterLeaseExpires_reclaims() {
        SessionId sessionId = persistCompletedSession();
        UUID eventId = UUID.randomUUID();
        Duration lease = Duration.ofSeconds(10);

        assertThat(processedEventStore.tryClaim(eventId, sessionId, "worker-a", lease))
                .isEqualTo(new ClaimResult.Acquired(1));

        entityManager.flush();
        entityManager.clear();
        jdbcClient.sql("""
                        UPDATE processed_event
                        SET claimed_at = :staleAt
                        WHERE event_id = :eventId
                        """)
                .param("staleAt", Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))))
                .param("eventId", eventId)
                .update();

        ClaimResult reclaimed = processedEventStore.tryClaim(eventId, sessionId, "worker-b", lease);

        assertThat(reclaimed).isEqualTo(new ClaimResult.Acquired(2));
    }

    private SessionId persistCompletedSession() {
        SessionId sessionId = SessionId.generate();
        entityManager.createNativeQuery(
                        "INSERT INTO interview_session (id, state) VALUES (:id, :state)")
                .setParameter("id", sessionId.value())
                .setParameter("state", "COMPLETED")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        return sessionId;
    }
}
