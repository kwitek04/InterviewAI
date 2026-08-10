package com.interviewai.report.adapter.in.messaging;

import com.interviewai.shared.InterviewCompletedEvent;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewCompletedMessageParserTest {

    private final InterviewCompletedMessageParser parser =
            new InterviewCompletedMessageParser(JsonMapper.builder().build());

    @Test
    @DisplayName("a valid envelope is parsed into InterviewCompletedEvent")
    void parse_validEnvelope_returnsEvent() {
        UUID eventId = UUID.randomUUID();
        SessionId sessionId = SessionId.generate();
        Instant completedAt = Instant.parse("2026-08-09T11:00:00Z");
        String body = """
                {"schemaVersion":1,"eventId":"%s","eventType":"InterviewCompleted","sessionId":"%s","completedAt":"%s"}
                """.formatted(eventId, sessionId.value(), completedAt);

        InterviewCompletedEvent event = parser.parse(body);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.sessionId()).isEqualTo(sessionId);
        assertThat(event.completedAt()).isEqualTo(completedAt);
    }

    @Test
    @DisplayName("unsupported schema versions are rejected")
    void parse_unsupportedSchema_throws() {
        assertThatThrownBy(() -> parser.parse("""
                {"schemaVersion":2,"eventId":"%s","eventType":"InterviewCompleted","sessionId":"%s","completedAt":"2026-08-09T11:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(MalformedInterviewCompletedMessageException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    @DisplayName("blank bodies are rejected")
    void parse_blank_throws() {
        assertThatThrownBy(() -> parser.parse(" "))
                .isInstanceOf(MalformedInterviewCompletedMessageException.class);
    }
}
