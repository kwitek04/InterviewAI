package com.interviewai.report.application;

import com.interviewai.report.application.port.out.ReportRepository;
import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.ReportStatus;
import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.application.SessionReportAccess;
import com.interviewai.shared.SessionId;
import org.springframework.stereotype.Service;

/**
 * Read-only API for interview report status and content.
 */
@Service
public class ReportQueryService {

    private final SessionApplicationService sessionApplicationService;
    private final ReportRepository reportRepository;

    public ReportQueryService(
            SessionApplicationService sessionApplicationService,
            ReportRepository reportRepository) {
        this.sessionApplicationService = sessionApplicationService;
        this.reportRepository = reportRepository;
    }

    /**
     * Returns the current report view for the given session.
     *
     * @throws ReportNotFoundException when the session is missing or not in a report workflow
     * @throws ReportFailedException when generation ended in a terminal failure
     */
    public ReportView getReport(SessionId sessionId) {
        SessionReportAccess access = sessionApplicationService.resolveReportAccess(sessionId);
        return switch (access) {
            case NOT_FOUND, NOT_ELIGIBLE -> throw new ReportNotFoundException(sessionId);
            case ELIGIBLE -> viewForEligibleSession(sessionId);
        };
    }

    private ReportView viewForEligibleSession(SessionId sessionId) {
        InterviewReport report = reportRepository.findBySessionId(sessionId).orElse(null);
        if (report == null) {
            return new ReportView.InProgress(ReportStatus.PENDING.name());
        }
        return switch (report.status()) {
            case PENDING, GENERATING -> new ReportView.InProgress(report.status().name());
            case READY -> new ReportView.Ready(report);
            case FAILED -> throw new ReportFailedException(
                    report.failureMessage() == null || report.failureMessage().isBlank()
                            ? ReportFailureMessages.UNEXPECTED_FAILURE
                            : report.failureMessage());
        };
    }
}
