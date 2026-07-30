package com.interviewai.report.application;

import com.interviewai.shared.SessionId;

/**
 * Thrown when no report exists for the requested session.
 */
public class ReportNotFoundException extends RuntimeException {

    private final SessionId sessionId;

    public ReportNotFoundException(SessionId sessionId) {
        super("Report not found for session " + sessionId.value());
        this.sessionId = sessionId;
    }

    public SessionId sessionId() {
        return sessionId;
    }
}
