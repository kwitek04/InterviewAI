package com.interviewai.report.application;

import com.interviewai.report.domain.InterviewReport;

/**
 * Read model returned by {@link ReportQueryService}.
 */
public sealed interface ReportView {

    /**
     * Report generation is still in progress.
     */
    record InProgress(String status) implements ReportView {
    }

    /**
     * Report is ready for display.
     */
    record Ready(InterviewReport report) implements ReportView {
    }
}
