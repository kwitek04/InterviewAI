package com.interviewai.report.application;

import com.interviewai.report.application.port.out.ProcessedEventStore;
import com.interviewai.report.application.port.out.ProcessedEventStore.ClaimResult;
import com.interviewai.shared.InterviewCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * Idempotent application entry point for interview-completed deliveries.
 */
@Service
public class InterviewCompletedProcessingService {

    private static final Logger log = LoggerFactory.getLogger(InterviewCompletedProcessingService.class);

    private final ProcessedEventStore processedEventStore;
    private final ReportApplicationService reportApplicationService;
    private final Duration claimLease;

    public InterviewCompletedProcessingService(
            ProcessedEventStore processedEventStore,
            ReportApplicationService reportApplicationService,
            @Value("${interviewai.report.worker.claim-lease}") Duration claimLease) {
        this.processedEventStore = processedEventStore;
        this.reportApplicationService = reportApplicationService;
        this.claimLease = claimLease;
        if (claimLease.isNegative() || claimLease.isZero()) {
            throw new IllegalArgumentException("claimLease must be positive");
        }
    }

    /**
     * Claims the event, generates the report when needed, and reports whether the
     * transport message may be acknowledged.
     *
     * @param receiveCount current SQS approximate receive count for this delivery
     * @param maxReceiveCount queue redrive limit; on the final attempt a safe FAILED
     *                        report is persisted before returning {@link InterviewCompletedProcessingOutcome#FAILED}
     */
    public InterviewCompletedProcessingOutcome process(
            InterviewCompletedEvent event,
            String workerId,
            int receiveCount,
            int maxReceiveCount) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(workerId, "workerId must not be null");
        if (receiveCount < 1) {
            throw new IllegalArgumentException("receiveCount must be >= 1");
        }
        if (maxReceiveCount < 1) {
            throw new IllegalArgumentException("maxReceiveCount must be >= 1");
        }

        ClaimResult claim = processedEventStore.tryClaim(
                event.eventId(), event.sessionId(), workerId, claimLease);

        return switch (claim) {
            case ClaimResult.AlreadyCompleted ignored -> {
                log.info(
                        "Duplicate interview-completed event {} for session {} — already completed",
                        event.eventId(),
                        event.sessionId().value());
                yield InterviewCompletedProcessingOutcome.DUPLICATE;
            }
            case ClaimResult.Busy ignored -> {
                log.info(
                        "Busy claim for interview-completed event {} session {} — skipping this delivery",
                        event.eventId(),
                        event.sessionId().value());
                yield InterviewCompletedProcessingOutcome.BUSY;
            }
            case ClaimResult.Acquired acquired -> {
                log.info(
                        "Acquired claim for interview-completed event {} session {} attempt {}",
                        event.eventId(),
                        event.sessionId().value(),
                        acquired.attemptCount());
                try {
                    reportApplicationService.generateReport(
                            event.sessionId(), event.eventId(), workerId);
                    yield InterviewCompletedProcessingOutcome.COMPLETED;
                } catch (RuntimeException exception) {
                    if (receiveCount >= maxReceiveCount) {
                        log.warn(
                                "Final SQS delivery for event {} session {} — persisting terminal FAILED report",
                                event.eventId(),
                                event.sessionId().value());
                        reportApplicationService.markTerminalFailure(event.sessionId(), exception);
                    }
                    log.warn(
                            "Report generation failed for event {} session {} receiveCount {}",
                            event.eventId(),
                            event.sessionId().value(),
                            receiveCount,
                            exception);
                    yield InterviewCompletedProcessingOutcome.FAILED;
                }
            }
        };
    }
}
