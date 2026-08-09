package com.interviewai.report.adapter.in.messaging;

/**
 * Thrown when an SQS body does not match the interview-completed wire contract.
 */
final class MalformedInterviewCompletedMessageException extends RuntimeException {

    MalformedInterviewCompletedMessageException(String message) {
        super(message);
    }

    MalformedInterviewCompletedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
