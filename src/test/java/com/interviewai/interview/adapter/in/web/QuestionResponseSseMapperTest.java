package com.interviewai.interview.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewai.interview.domain.QuestionResponseEventType;
import com.interviewai.interview.domain.QuestionResponseStreamEvent;
import com.interviewai.shared.ResponseId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionResponseSseMapperTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private ObjectMapper objectMapper;
    private QuestionResponseSseMapper mapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mapper = new QuestionResponseSseMapper();
    }

    @Test
    @DisplayName("token events map to token SSE shape")
    void toSseEvent_tokenEvent_mapsContract() throws Exception {
        SseEventTestSupport.CapturedSseEvent captured = SseEventTestSupport.capture(
                mapper.toSseEvent(event(QuestionResponseEventType.TOKEN, 1, "Spring")),
                objectMapper);

        assertThat(captured.id()).isEqualTo("1");
        assertThat(captured.eventName()).isEqualTo("token");
        assertThat(captured.jsonPayload()).isEqualTo("{\"text\":\"Spring\"}");
    }

    @Test
    @DisplayName("completed events map to completed SSE shape")
    void toSseEvent_completedEvent_mapsContract() throws Exception {
        SseEventTestSupport.CapturedSseEvent captured = SseEventTestSupport.capture(
                mapper.toSseEvent(event(QuestionResponseEventType.COMPLETED, 3, "How did you use Spring Boot?")),
                objectMapper);

        assertThat(captured.eventName()).isEqualTo("completed");
        assertThat(captured.jsonPayload()).isEqualTo("{\"question\":\"How did you use Spring Boot?\"}");
    }

    @Test
    @DisplayName("error events map to error SSE shape")
    void toSseEvent_errorEvent_mapsContract() throws Exception {
        SseEventTestSupport.CapturedSseEvent captured = SseEventTestSupport.capture(
                mapper.toSseEvent(event(QuestionResponseEventType.ERROR, 4, "Question generation failed.")),
                objectMapper);

        assertThat(captured.eventName()).isEqualTo("error");
        assertThat(captured.jsonPayload()).isEqualTo("{\"message\":\"Question generation failed.\"}");
    }

    private QuestionResponseStreamEvent event(QuestionResponseEventType type, int sequence, String payload) {
        return new QuestionResponseStreamEvent(
                new ResponseId(UUID.randomUUID()),
                sequence,
                type,
                payload,
                NOW);
    }
}
