package com.interviewai.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewCompletedEventTest {

    @Test
    @DisplayName("creates an immutable completion event with all required identifiers")
    void createsImmutableEvent() {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        SessionId sessionId = SessionId.generate();
        Instant completedAt = Instant.parse("2026-01-01T10:00:00Z");

        InterviewCompletedEvent event = new InterviewCompletedEvent(eventId, sessionId, completedAt);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.sessionId()).isEqualTo(sessionId);
        assertThat(event.completedAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("rejects null required fields")
    void rejectsNullFields() {
        UUID eventId = UUID.randomUUID();
        SessionId sessionId = SessionId.generate();
        Instant completedAt = Instant.parse("2026-01-01T10:00:00Z");

        assertThatThrownBy(() -> new InterviewCompletedEvent(null, sessionId, completedAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InterviewCompletedEvent(eventId, null, completedAt))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new InterviewCompletedEvent(eventId, sessionId, null))
                .isInstanceOf(NullPointerException.class);
    }
}
