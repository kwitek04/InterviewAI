package com.interviewai.report.application;

import com.interviewai.report.application.port.out.ReportRepository;
import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.application.SessionReportAccess;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportQueryServiceTest {

    private static final SessionId SESSION_ID = SessionId.generate();
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock
    private SessionApplicationService sessionApplicationService;

    @Mock
    private ReportRepository reportRepository;

    private ReportQueryService service;

    @BeforeEach
    void setUp() {
        service = new ReportQueryService(sessionApplicationService, reportRepository);
    }

    @Test
    @DisplayName("eligible session without a report row is treated as PENDING")
    void getReport_eligibleWithoutRow_returnsPending() {
        when(sessionApplicationService.resolveReportAccess(SESSION_ID)).thenReturn(SessionReportAccess.ELIGIBLE);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.empty());

        assertThat(service.getReport(SESSION_ID)).isEqualTo(new ReportView.InProgress("PENDING"));
    }

    @Test
    @DisplayName("a READY report is returned for display")
    void getReport_ready_returnsReadyView() {
        when(sessionApplicationService.resolveReportAccess(SESSION_ID)).thenReturn(SessionReportAccess.ELIGIBLE);
        InterviewReport ready = InterviewReport.pending(UUID.randomUUID(), SESSION_ID, NOW)
                .markGenerating(NOW)
                .markReady(
                        List.of(new QuestionAssessment(0, "Q", "A", 4, "Good")),
                        List.of("s"),
                        List.of("w"),
                        List.of("r"),
                        NOW);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(ready));

        assertThat(service.getReport(SESSION_ID)).isEqualTo(new ReportView.Ready(ready));
    }

    @Test
    @DisplayName("a FAILED report surfaces only the safe failure message")
    void getReport_failed_throwsSafeFailure() {
        when(sessionApplicationService.resolveReportAccess(SESSION_ID)).thenReturn(SessionReportAccess.ELIGIBLE);
        InterviewReport failed = InterviewReport.pending(UUID.randomUUID(), SESSION_ID, NOW)
                .markFailed(ReportFailureMessages.GENERATION_FAILED, NOW);
        when(reportRepository.findBySessionId(SESSION_ID)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.getReport(SESSION_ID))
                .isInstanceOf(ReportFailedException.class)
                .hasMessage(ReportFailureMessages.GENERATION_FAILED);
    }

    @Test
    @DisplayName("unknown or ineligible sessions are not found")
    void getReport_notEligible_throwsNotFound() {
        when(sessionApplicationService.resolveReportAccess(SESSION_ID)).thenReturn(SessionReportAccess.NOT_ELIGIBLE);

        assertThatThrownBy(() -> service.getReport(SESSION_ID))
                .isInstanceOf(ReportNotFoundException.class);
    }
}
