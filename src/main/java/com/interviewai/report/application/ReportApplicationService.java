package com.interviewai.report.application;

import com.interviewai.report.application.port.out.ProcessedEventStore;
import com.interviewai.report.application.port.out.ReportGenerator;
import com.interviewai.report.application.port.out.ReportRepository;
import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.report.domain.ReportStatus;
import com.interviewai.session.application.CompletedInterviewSnapshot;
import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.shared.SessionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Orchestrates multi-stage report generation, validation, persistence, and session status updates.
 */
@Service
public class ReportApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReportApplicationService.class);

    private final SessionApplicationService sessionApplicationService;
    private final ReportRepository reportRepository;
    private final ReportGenerator reportGenerator;
    private final ProcessedEventStore processedEventStore;
    private final ReportGenerationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ReportApplicationService(
            SessionApplicationService sessionApplicationService,
            ReportRepository reportRepository,
            ReportGenerator reportGenerator,
            ProcessedEventStore processedEventStore,
            ReportGenerationProperties properties,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.sessionApplicationService = sessionApplicationService;
        this.reportRepository = reportRepository;
        this.reportGenerator = reportGenerator;
        this.processedEventStore = processedEventStore;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * Generates a report for the given completed interview and marks the session report-ready.
     */
    public InterviewReport generateReport(SessionId sessionId) {
        return generateReport(sessionId, null, null);
    }

    /**
     * Generates a report and, when {@code eventId} is present, marks the processing claim
     * completed in the same database transaction as the READY report and session update.
     */
    public InterviewReport generateReport(SessionId sessionId, UUID eventId, String claimOwnerId) {
        CompletedInterviewSnapshot snapshot = sessionApplicationService.requireCompletedInterviewSnapshot(sessionId);
        if (snapshot.answeredQuestions().isEmpty()) {
            throw new InvalidReportContentException("Completed interview has no answered questions");
        }

        InterviewReport existing = reportRepository.findBySessionId(sessionId).orElse(null);
        if (existing != null && existing.isReady()) {
            return transactionTemplate.execute(status -> {
                sessionApplicationService.markReportReady(sessionId);
                completeClaimIfPresent(eventId, claimOwnerId);
                return existing;
            });
        }

        InterviewReport generating = transactionTemplate.execute(status -> {
            InterviewReport report = existing == null
                    ? InterviewReport.pending(UUID.randomUUID(), sessionId, clock.instant())
                    : existing;
            return reportRepository.save(report.markGenerating(clock.instant()));
        });

        try {
            List<QuestionAssessment> assessments = runWithRetries(
                    "evaluateAnswers",
                    () -> QuestionAssessmentValidator.validateAndBind(
                            snapshot, reportGenerator.evaluateAnswers(snapshot)));
            SynthesisResult synthesis = runWithRetries(
                    "synthesize",
                    () -> reportGenerator.synthesize(snapshot, assessments));

            return transactionTemplate.execute(status -> {
                InterviewReport current = reportRepository.findById(generating.id())
                        .orElseThrow(() -> new ReportNotFoundException(sessionId));
                InterviewReport saved = reportRepository.save(current.markReady(
                        assessments,
                        synthesis.strengths(),
                        synthesis.weaknesses(),
                        synthesis.recommendations(),
                        clock.instant()));
                sessionApplicationService.markReportReady(sessionId);
                completeClaimIfPresent(eventId, claimOwnerId);
                return saved;
            });
        } catch (RuntimeException exception) {
            log.warn("Report generation failed for session {}", sessionId.value(), exception);
            transactionTemplate.executeWithoutResult(status -> reportRepository.findById(generating.id())
                    .ifPresent(current -> {
                        if (current.status() == ReportStatus.GENERATING || current.status() == ReportStatus.PENDING) {
                            reportRepository.save(
                                    current.markFailed(ReportFailureMessages.of(exception), clock.instant()));
                        }
                    }));
            if (exception instanceof ReportGenerationException reportGenerationException) {
                throw reportGenerationException;
            }
            throw new ReportGenerationException("Report generation failed for session " + sessionId.value(), exception);
        }
    }

    /**
     * Persists a safe terminal FAILED report when SQS is about to dead-letter the delivery.
     */
    public void markTerminalFailure(SessionId sessionId, RuntimeException cause) {
        String message = ReportFailureMessages.of(cause);
        transactionTemplate.executeWithoutResult(status -> {
            InterviewReport existing = reportRepository.findBySessionId(sessionId).orElse(null);
            if (existing != null && existing.isReady()) {
                return;
            }
            if (existing == null) {
                InterviewReport pending = InterviewReport.pending(UUID.randomUUID(), sessionId, clock.instant());
                reportRepository.save(pending.markFailed(message, clock.instant()));
                return;
            }
            if (existing.status() == ReportStatus.FAILED) {
                return;
            }
            reportRepository.save(existing.markFailed(message, clock.instant()));
        });
    }

    public InterviewReport getBySessionId(SessionId sessionId) {
        return reportRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ReportNotFoundException(sessionId));
    }

    private void completeClaimIfPresent(UUID eventId, String claimOwnerId) {
        if (eventId != null) {
            processedEventStore.markCompleted(eventId, claimOwnerId);
        }
    }

    private <T> T runWithRetries(String stageName, Supplier<T> action) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return action.get();
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.info("Report stage '{}' attempt {}/{} failed", stageName, attempt, properties.maxAttempts());
            }
        }
        throw new ReportGenerationException(
                "Report stage '" + stageName + "' failed after " + properties.maxAttempts() + " attempts",
                lastFailure);
    }
}
