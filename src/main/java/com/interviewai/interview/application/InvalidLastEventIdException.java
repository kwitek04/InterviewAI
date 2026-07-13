package com.interviewai.interview.application;

/**
 * Thrown when the {@code Last-Event-ID} request header is malformed or invalid.
 */
public final class InvalidLastEventIdException extends RuntimeException {

    private final String lastEventId;

    public InvalidLastEventIdException(String lastEventId) {
        super("Last-Event-ID must be a non-negative integer");
        this.lastEventId = lastEventId;
    }

    public String lastEventId() {
        return lastEventId;
    }
}
