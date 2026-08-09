package com.interviewai.report.adapter.out.persistence;

import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.report.domain.ReportStatus;
import com.interviewai.shared.SessionId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ReportPersistenceAdapter.class)
class ReportPersistenceAdapterIT {

    private static final Instant NOW = Instant.parse("2026-07-30T14:00:00Z");

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
    private ReportPersistenceAdapter reportAdapter;

    @Autowired
    private InterviewReportJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("READY report JSONB payload round-trips after flush and clear")
    void saveReady_thenFindBySessionId_reconstructsPayload() {
        SessionId sessionId = persistCompletedSession();
        InterviewReport ready = InterviewReport.pending(UUID.randomUUID(), sessionId, NOW)
                .markGenerating(NOW.plusSeconds(1))
                .markReady(
                        List.of(new QuestionAssessment(0, "Tell me about yourself", "I am a backend developer", 4, "Solid")),
                        List.of("Clear communicator"),
                        List.of("Needs deeper examples"),
                        List.of("Prepare STAR stories"),
                        NOW.plusSeconds(2));

        reportAdapter.save(InterviewReport.pending(ready.id(), sessionId, NOW).markGenerating(NOW.plusSeconds(1)));
        reportAdapter.save(ready);
        flushAndClear();

        InterviewReport reloaded = reportAdapter.findBySessionId(sessionId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ReportStatus.READY);
        assertThat(reloaded.assessments()).containsExactly(
                new QuestionAssessment(0, "Tell me about yourself", "I am a backend developer", 4, "Solid"));
        assertThat(reloaded.strengths()).containsExactly("Clear communicator");
        assertThat(reloaded.weaknesses()).containsExactly("Needs deeper examples");
        assertThat(reloaded.recommendations()).containsExactly("Prepare STAR stories");
        assertThat(reloaded.generatedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    @DisplayName("session_id is unique across reports")
    void save_secondReportForSameSession_fails() {
        SessionId sessionId = persistCompletedSession();
        reportAdapter.save(InterviewReport.pending(UUID.randomUUID(), sessionId, NOW));
        flushAndClear();

        assertThatThrownBy(() -> {
            reportAdapter.save(InterviewReport.pending(UUID.randomUUID(), sessionId, NOW));
            flushAndClear();
        }).isInstanceOfAny(
                DataIntegrityViolationException.class,
                ConstraintViolationException.class,
                PersistenceException.class);
    }

    @Test
    @DisplayName("status transitions remain durable after clearing the persistence context")
    void saveGeneratingThenFailed_statusSurvivesReload() {
        SessionId sessionId = persistCompletedSession();
        InterviewReport pending = InterviewReport.pending(UUID.randomUUID(), sessionId, NOW);
        reportAdapter.save(pending);
        flushAndClear();

        InterviewReport generating = reportAdapter.findById(pending.id()).orElseThrow()
                .markGenerating(NOW.plusSeconds(1));
        reportAdapter.save(generating);
        flushAndClear();

        InterviewReport failed = reportAdapter.findById(pending.id()).orElseThrow()
                .markFailed("provider timeout", NOW.plusSeconds(2));
        reportAdapter.save(failed);
        flushAndClear();

        InterviewReport reloaded = reportAdapter.findById(pending.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(reloaded.failureMessage()).isEqualTo("provider timeout");
    }

    @Test
    @DisplayName("saving a report read before a concurrent update is rejected by the port")
    void save_staleAggregateThroughPort_throwsOptimisticLock() {
        SessionId sessionId = persistCompletedSession();
        InterviewReport pending = InterviewReport.pending(UUID.randomUUID(), sessionId, NOW);
        reportAdapter.save(pending);
        flushAndClear();

        InterviewReport staleReader = reportAdapter.findById(pending.id()).orElseThrow();
        InterviewReport winner = reportAdapter.findById(pending.id()).orElseThrow();

        reportAdapter.save(winner.markGenerating(NOW.plusSeconds(1)));
        flushAndClear();

        assertThat(reportAdapter.findById(pending.id()).orElseThrow().status())
                .isEqualTo(ReportStatus.GENERATING);
        assertThatThrownBy(() -> reportAdapter.save(staleReader.markFailed("stale writer", NOW.plusSeconds(2))))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("concurrent updates with a stale version fail optimistic locking")
    void save_withStaleVersion_throwsOptimisticLock() {
        SessionId sessionId = persistCompletedSession();
        InterviewReport pending = InterviewReport.pending(UUID.randomUUID(), sessionId, NOW);
        reportAdapter.save(pending);
        flushAndClear();

        InterviewReportEntity first = jpaRepository.findById(pending.id()).orElseThrow();
        entityManager.detach(first);

        InterviewReport generating = reportAdapter.findById(pending.id()).orElseThrow()
                .markGenerating(NOW.plusSeconds(1));
        reportAdapter.save(generating);
        flushAndClear();

        first.setStatus(ReportStatus.FAILED.name());
        first.setFailureMessage("stale");
        first.setUpdatedAt(NOW.plusSeconds(9));

        assertThatThrownBy(() -> {
            jpaRepository.save(first);
            flushAndClear();
        }).isInstanceOfAny(ObjectOptimisticLockingFailureException.class, OptimisticLockException.class);
    }

    private SessionId persistCompletedSession() {
        SessionId sessionId = SessionId.generate();
        entityManager.createNativeQuery(
                        "INSERT INTO interview_session (id, state) VALUES (:id, :state)")
                .setParameter("id", sessionId.value())
                .setParameter("state", "COMPLETED")
                .executeUpdate();
        flushAndClear();
        return sessionId;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
