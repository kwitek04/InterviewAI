package com.interviewai.interview.adapter.in.web;

import com.interviewai.interview.application.QuestionResponseNotFoundException;
import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InterviewStreamControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private QuestionResponseStreamFeeder streamFeeder;

    private MockMvc mockMvc;
    private SessionId sessionId;
    private ResponseId responseId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InterviewStreamController(streamFeeder))
                .setControllerAdvice(new InterviewStreamExceptionHandler())
                .build();
        sessionId = SessionId.generate();
        responseId = ResponseId.generate();
    }

    @Test
    @DisplayName("GET /events opens an SSE stream for a valid response")
    void streamEvents_validRequest_startsSseStream() throws Exception {
        when(streamFeeder.open(eq(sessionId), eq(responseId), eq(0))).thenReturn(new SseEmitter(60_000L));

        mockMvc.perform(get(
                        "/api/v1/sessions/{sessionId}/responses/{responseId}/events",
                        sessionId.value(),
                        responseId.value())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        verify(streamFeeder).open(sessionId, responseId, 0);
    }

    @Test
    @DisplayName("GET /events passes Last-Event-ID as replay cursor")
    void streamEvents_withLastEventId_passesReplayCursor() throws Exception {
        when(streamFeeder.open(eq(sessionId), eq(responseId), eq(2))).thenReturn(new SseEmitter(60_000L));

        mockMvc.perform(get(
                        "/api/v1/sessions/{sessionId}/responses/{responseId}/events",
                        sessionId.value(),
                        responseId.value())
                        .header("Last-Event-ID", "2")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(streamFeeder).open(sessionId, responseId, 2);
    }

    @Test
    @DisplayName("GET /events returns 400 for malformed Last-Event-ID")
    void streamEvents_malformedLastEventId_returns400() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/sessions/{sessionId}/responses/{responseId}/events",
                        sessionId.value(),
                        responseId.value())
                        .header("Last-Event-ID", "not-a-number")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /events returns 404 for unknown response")
    void streamEvents_unknownResponse_returns404() throws Exception {
        when(streamFeeder.open(eq(sessionId), eq(responseId), anyInt()))
                .thenThrow(new QuestionResponseNotFoundException(responseId));

        mockMvc.perform(get(
                        "/api/v1/sessions/{sessionId}/responses/{responseId}/events",
                        sessionId.value(),
                        responseId.value())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /events returns 404 for response and session mismatch")
    void streamEvents_responseSessionMismatch_returns404() throws Exception {
        when(streamFeeder.open(eq(sessionId), any(ResponseId.class), anyInt()))
                .thenThrow(new QuestionResponseNotFoundException(responseId));

        mockMvc.perform(get(
                        "/api/v1/sessions/{sessionId}/responses/{responseId}/events",
                        sessionId.value(),
                        UUID.randomUUID())
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }
}
