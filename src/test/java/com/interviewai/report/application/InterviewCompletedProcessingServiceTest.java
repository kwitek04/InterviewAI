package com.interviewai.report.application;

import com.interviewai.report.application.port.out.ProcessedEventStore;
import com.interviewai.report.application.port.out.ProcessedEventStore.ClaimResult;
import com.interviewai.report.domain.InterviewReport;
import com.interviewai.shared.InterviewCompletedEvent;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewCompletedProcessingServiceTest {

    private static final SessionId SESSION_ID = SessionId.generate();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final InterviewCompletedEvent EVENT =
            new InterviewCompletedEvent(EVENT_ID, SESSION_ID, Instant.parse("2026-08-09T11:00:00Z"));

    @Mock
    private ProcessedEventStore processedEventStore;

    @Mock
    private ReportApplicationService reportApplicationService;

    private InterviewCompletedProcessingService service;

    @BeforeEach
    void setUp() {
        service = new InterviewCompletedProcessingService(
                processedEventStore, reportApplicationService, Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("an acquired claim generates the report")
    void process_acquired_generatesReport() {
        when(processedEventStore.tryClaim(eq(EVENT_ID), eq(SESSION_ID), eq("worker-1"), any()))
                .thenReturn(new ClaimResult.Acquired(1));
        when(reportApplicationService.generateReport(SESSION_ID, EVENT_ID, "worker-1"))
                .thenReturn(InterviewReport.pending(UUID.randomUUID(), SESSION_ID, Instant.now()));

        assertThat(service.process(EVENT, "worker-1", 1, 5))
                .isEqualTo(InterviewCompletedProcessingOutcome.COMPLETED);
        verify(reportApplicationService).generateReport(SESSION_ID, EVENT_ID, "worker-1");
    }

    @Test
    @DisplayName("an already completed claim is reported as duplicate")
    void process_alreadyCompleted_isDuplicate() {
        when(processedEventStore.tryClaim(eq(EVENT_ID), eq(SESSION_ID), eq("worker-1"), any()))
                .thenReturn(new ClaimResult.AlreadyCompleted());

        assertThat(service.process(EVENT, "worker-1", 1, 5))
                .isEqualTo(InterviewCompletedProcessingOutcome.DUPLICATE);
        verify(reportApplicationService, never()).generateReport(any(), any(), any());
    }

    @Test
    @DisplayName("a busy claim does not start generation")
    void process_busy_skipsGeneration() {
        when(processedEventStore.tryClaim(eq(EVENT_ID), eq(SESSION_ID), eq("worker-1"), any()))
                .thenReturn(new ClaimResult.Busy());

        assertThat(service.process(EVENT, "worker-1", 1, 5))
                .isEqualTo(InterviewCompletedProcessingOutcome.BUSY);
        verify(reportApplicationService, never()).generateReport(any(), any(), any());
    }

    @Test
    @DisplayName("the final failed delivery persists terminal FAILED metadata")
    void process_finalFailure_marksTerminalFailure() {
        when(processedEventStore.tryClaim(eq(EVENT_ID), eq(SESSION_ID), eq("worker-1"), any()))
                .thenReturn(new ClaimResult.Acquired(3));
        when(reportApplicationService.generateReport(SESSION_ID, EVENT_ID, "worker-1"))
                .thenThrow(new ReportGenerationException("boom"));

        assertThat(service.process(EVENT, "worker-1", 5, 5))
                .isEqualTo(InterviewCompletedProcessingOutcome.FAILED);
        verify(reportApplicationService).markTerminalFailure(eq(SESSION_ID), any());
    }
}
