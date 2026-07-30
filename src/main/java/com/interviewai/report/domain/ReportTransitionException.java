package com.interviewai.report.domain;

/**
 * Thrown when a report status transition is not allowed.
 */
public class ReportTransitionException extends RuntimeException {

    private final ReportStatus from;
    private final ReportStatus to;

    public ReportTransitionException(ReportStatus from, ReportStatus to) {
        super("Cannot transition report status from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public ReportStatus from() {
        return from;
    }

    public ReportStatus to() {
        return to;
    }
}
