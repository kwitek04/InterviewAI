package com.interviewai.report.application;

/**
 * Thrown when a report reached a terminal FAILED state that is safe to surface to clients.
 */
public final class ReportFailedException extends RuntimeException {

    private final String safeMessage;

    public ReportFailedException(String safeMessage) {
        super(safeMessage);
        this.safeMessage = safeMessage;
    }

    public String safeMessage() {
        return safeMessage;
    }
}
