package com.interviewai.session.application;

import com.interviewai.shared.InterviewCompletedEvent;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewCompletedMessageMapperTest {

    @Test
    @DisplayName("maps the completion event to the versioned JSON body with flat UUID fields")
    void toJsonBody_usesFlatUuidContract() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SessionId sessionId = new SessionId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        Instant completedAt = Instant.parse("2026-07-15T20:00:00Z");

        String body = InterviewCompletedMessageMapper.toJsonBody(
                new InterviewCompletedEvent(eventId, sessionId, completedAt));

        assertThat(body).isEqualTo(
                "{\"schemaVersion\":1,\"eventId\":\"11111111-1111-1111-1111-111111111111\","
                        + "\"eventType\":\"InterviewCompleted\","
                        + "\"sessionId\":\"22222222-2222-2222-2222-222222222222\","
                        + "\"completedAt\":\"2026-07-15T20:00:00Z\"}");
    }

    @Test
    @DisplayName("maps required SQS message attributes")
    void toMessageAttributes_containsRequiredAttributes() {
        InterviewCompletedEvent event = new InterviewCompletedEvent(
                UUID.randomUUID(),
                SessionId.generate(),
                Instant.parse("2026-07-15T20:00:00Z"));

        Map<String, String> attributes = InterviewCompletedMessageMapper.toMessageAttributes(event);

        assertThat(attributes).containsExactlyInAnyOrderEntriesOf(Map.of(
                "contentType", "application/json",
                "eventType", "InterviewCompleted",
                "schemaVersion", "1"));
    }
}
