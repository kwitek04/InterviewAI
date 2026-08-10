package com.interviewai.report.adapter.in.messaging;

import com.interviewai.shared.InterviewCompletedEvent;
import com.interviewai.shared.SessionId;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Parses and validates the fixed interview-completed SQS JSON envelope.
 */
final class InterviewCompletedMessageParser {

    static final String EVENT_TYPE = "InterviewCompleted";
    static final int SCHEMA_VERSION = 1;

    private final JsonMapper jsonMapper;

    InterviewCompletedMessageParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    InterviewCompletedEvent parse(String body) {
        if (body == null || body.isBlank()) {
            throw new MalformedInterviewCompletedMessageException("Message body is blank");
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (RuntimeException exception) {
            throw new MalformedInterviewCompletedMessageException("Message body is not valid JSON", exception);
        }
        if (!root.isObject()) {
            throw new MalformedInterviewCompletedMessageException("Message body must be a JSON object");
        }

        int schemaVersion = requiredInt(root, "schemaVersion");
        if (schemaVersion != SCHEMA_VERSION) {
            throw new MalformedInterviewCompletedMessageException(
                    "Unsupported schemaVersion " + schemaVersion);
        }

        String eventType = requiredText(root, "eventType");
        if (!EVENT_TYPE.equals(eventType)) {
            throw new MalformedInterviewCompletedMessageException("Unsupported eventType " + eventType);
        }

        UUID eventId = requiredUuid(root, "eventId");
        UUID sessionId = requiredUuid(root, "sessionId");
        Instant completedAt = requiredInstant(root, "completedAt");
        return new InterviewCompletedEvent(eventId, new SessionId(sessionId), completedAt);
    }

    private static int requiredInt(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isNumber()) {
            throw new MalformedInterviewCompletedMessageException("Missing or invalid field '" + field + "'");
        }
        return node.asInt();
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isString() || node.asString().isBlank()) {
            throw new MalformedInterviewCompletedMessageException("Missing or invalid field '" + field + "'");
        }
        return node.asString();
    }

    private static UUID requiredUuid(JsonNode root, String field) {
        try {
            return UUID.fromString(requiredText(root, field));
        } catch (IllegalArgumentException exception) {
            throw new MalformedInterviewCompletedMessageException("Invalid UUID in field '" + field + "'", exception);
        }
    }

    private static Instant requiredInstant(JsonNode root, String field) {
        try {
            return Instant.parse(requiredText(root, field));
        } catch (RuntimeException exception) {
            throw new MalformedInterviewCompletedMessageException(
                    "Invalid instant in field '" + field + "'", exception);
        }
    }
}
