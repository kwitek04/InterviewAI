package com.interviewai.report.application;

/**
 * Thrown when generated report content violates business validation rules.
 */
public class InvalidReportContentException extends RuntimeException {

    public InvalidReportContentException(String message) {
        super(message);
    }
}
