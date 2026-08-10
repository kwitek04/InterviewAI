package com.interviewai.session.application;

import com.interviewai.shared.InterviewCompletedEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps {@link InterviewCompletedEvent} to the versioned SQS JSON contract.
 */
public final class InterviewCompletedMessageMapper {

    public static final String EVENT_TYPE = "InterviewCompleted";
    public static final int SCHEMA_VERSION = 1;
    public static final String ATTRIBUTE_CONTENT_TYPE = "contentType";
    public static final String ATTRIBUTE_EVENT_TYPE = "eventType";
    public static final String ATTRIBUTE_SCHEMA_VERSION = "schemaVersion";
    public static final String CONTENT_TYPE_JSON = "application/json";

    private InterviewCompletedMessageMapper() {
    }

    public static String toJsonBody(InterviewCompletedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return """
                {"schemaVersion":%d,"eventId":"%s","eventType":"%s","sessionId":"%s","completedAt":"%s"}
                """.formatted(
                        SCHEMA_VERSION,
                        event.eventId(),
                        EVENT_TYPE,
                        event.sessionId().value(),
                        event.completedAt())
                .trim();
    }

    public static Map<String, String> toMessageAttributes(InterviewCompletedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(ATTRIBUTE_CONTENT_TYPE, CONTENT_TYPE_JSON);
        attributes.put(ATTRIBUTE_EVENT_TYPE, EVENT_TYPE);
        attributes.put(ATTRIBUTE_SCHEMA_VERSION, Integer.toString(SCHEMA_VERSION));
        return Map.copyOf(attributes);
    }
}
