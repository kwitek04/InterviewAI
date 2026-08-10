package com.interviewai.report.adapter.out.persistence;

import com.interviewai.report.application.port.out.ReportRepository;
import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.report.domain.ReportStatus;
import com.interviewai.shared.SessionId;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
class ReportPersistenceAdapter implements ReportRepository {

    private final InterviewReportJpaRepository repository;
    private final JsonMapper jsonMapper;

    ReportPersistenceAdapter(InterviewReportJpaRepository repository) {
        this.repository = repository;
        this.jsonMapper = JsonMapper.builder().build();
    }

    @Override
    @Transactional
    public InterviewReport save(InterviewReport report) {
        Objects.requireNonNull(report, "report must not be null");
        InterviewReportEntity entity = repository.findById(report.id())
                .map(stored -> requireExpectedVersion(stored, report))
                .orElseGet(() -> InterviewReportEntity.create(
                        report.id(),
                        report.sessionId().value(),
                        ReportStatusMapper.toStorage(report.status()),
                        report.createdAt()));

        entity.setStatus(ReportStatusMapper.toStorage(report.status()));
        entity.setUpdatedAt(report.updatedAt());
        entity.setGeneratedAt(report.generatedAt());
        entity.setFailureMessage(report.failureMessage());

        if (report.status() == ReportStatus.READY) {
            entity.setPayload(writePayload(ReportPayloadJson.fromReady(
                    report.assessments(),
                    report.strengths(),
                    report.weaknesses(),
                    report.recommendations(),
                    report.generatedAt())));
        } else if (report.status() == ReportStatus.PENDING || report.status() == ReportStatus.GENERATING) {
            if (report.assessments().isEmpty()) {
                entity.setPayload(null);
            }
        }

        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewReport> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InterviewReport> findBySessionId(SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return repository.findBySessionId(sessionId.value()).map(this::toDomain);
    }

    /**
     * Rejects writes derived from an aggregate that was read before a concurrent update,
     * so a stale in-memory report cannot silently overwrite newer state.
     */
    private static InterviewReportEntity requireExpectedVersion(
            InterviewReportEntity stored, InterviewReport report) {
        if (stored.getVersion() != report.version()) {
            throw new ObjectOptimisticLockingFailureException(InterviewReport.class, report.id());
        }
        return stored;
    }

    private InterviewReport toDomain(InterviewReportEntity entity) {
        ReportStatus status = ReportStatusMapper.fromStorage(entity.getStatus());
        List<QuestionAssessment> assessments = List.of();
        List<String> strengths = List.of();
        List<String> weaknesses = List.of();
        List<String> recommendations = List.of();

        if (entity.getPayload() != null && !entity.getPayload().isBlank()) {
            ReportPayloadJson payload = readPayload(entity.getPayload());
            assessments = payload.questionAssessments().stream()
                    .map(ReportPayloadJson.QuestionAssessmentJson::toDomain)
                    .toList();
            strengths = payload.strengths();
            weaknesses = payload.weaknesses();
            recommendations = payload.recommendations();
        }

        return new InterviewReport(
                entity.getId(),
                new SessionId(entity.getSessionId()),
                status,
                assessments,
                strengths,
                weaknesses,
                recommendations,
                entity.getFailureMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getGeneratedAt(),
                entity.getVersion());
    }

    private String writePayload(ReportPayloadJson payload) {
        try {
            return jsonMapper.writeValueAsString(payload);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize report payload", exception);
        }
    }

    private ReportPayloadJson readPayload(String json) {
        try {
            return jsonMapper.readValue(json, ReportPayloadJson.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to deserialize report payload", exception);
        }
    }
}
