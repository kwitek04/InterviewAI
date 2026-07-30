package com.interviewai.report.application.port.out;

import com.interviewai.report.domain.InterviewReport;
import com.interviewai.shared.SessionId;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for interview reports.
 */
public interface ReportRepository {

    InterviewReport save(InterviewReport report);

    Optional<InterviewReport> findById(UUID id);

    Optional<InterviewReport> findBySessionId(SessionId sessionId);
}
