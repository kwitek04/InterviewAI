package com.interviewai.session.adapter.in.web;

import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.application.SessionNotFoundException;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.session.domain.SessionTransitionException;
import com.interviewai.shared.CvId;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SessionControllerIT {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionApplicationService sessionApplicationService;

    @Test
    @DisplayName("POST /api/v1/sessions starts a session and returns 201 with its first question")
    void startSession_returnsFirstQuestion() throws Exception {
        SessionId id = SessionId.generate();
        InterviewSession session = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionApplicationService.startInterview(java.util.Optional.empty())).thenReturn(session);

        mockMvc.perform(post("/api/v1/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(id.value().toString()))
                .andExpect(jsonPath("$.question").value("Tell me about yourself."));
    }

    @Test
    @DisplayName("POST /api/v1/sessions accepts optional cvId body")
    void startSession_withCvId_returnsCreated() throws Exception {
        SessionId id = SessionId.generate();
        UUID cvId = UUID.randomUUID();
        InterviewSession session = InterviewSession.create(id, new CvId(cvId))
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about your Kafka project.", NOW));
        when(sessionApplicationService.startInterview(eq(java.util.Optional.of(new CvId(cvId))))).thenReturn(session);

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cvId\":\"" + cvId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(id.value().toString()))
                .andExpect(jsonPath("$.question").value("Tell me about your Kafka project."));
    }

    @Test
    @DisplayName("POST /api/v1/sessions returns 404 for unknown cvId")
    void startSession_withUnknownCvId_returns404() throws Exception {
        UUID cvId = UUID.randomUUID();
        when(sessionApplicationService.startInterview(eq(java.util.Optional.of(new CvId(cvId)))))
                .thenThrow(new com.interviewai.cv.application.CvNotFoundException(new CvId(cvId)));

        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cvId\":\"" + cvId + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/answers rejects a blank answer with 400")
    void submitAnswer_withBlankAnswer_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/answers", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/answers returns the next question")
    void submitAnswer_returnsNextQuestion() throws Exception {
        SessionId id = SessionId.generate();
        InterviewSession session = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW))
                .apply(new SessionCommand.SubmitAnswer("I am a backend developer.", NOW))
                .apply(new SessionCommand.AskQuestion("What is your experience with Spring Boot?", NOW));
        when(sessionApplicationService.submitAnswer(eq(id), eq("I am a backend developer.")))
                .thenReturn(session);

        mockMvc.perform(post("/api/v1/sessions/{id}/answers", id.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"I am a backend developer.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id.value().toString()))
                .andExpect(jsonPath("$.question").value("What is your experience with Spring Boot?"));
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/answers returns 409 when the session rejects the command")
    void submitAnswer_whenSessionRejectsCommand_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionApplicationService.submitAnswer(any(SessionId.class), any(String.class)))
                .thenThrow(new SessionTransitionException(
                        new SessionState.Created(), new SessionCommand.SubmitAnswer("hi", NOW)));

        mockMvc.perform(post("/api/v1/sessions/{id}/answers", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"hi\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/v1/sessions/{id} returns 404 when the session does not exist")
    void getSession_whenNotFound_returns404() throws Exception {
        SessionId id = SessionId.generate();
        when(sessionApplicationService.getSession(id)).thenThrow(new SessionNotFoundException(id));

        mockMvc.perform(get("/api/v1/sessions/{id}", id.value()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/sessions/{id} returns the current state and transcript")
    void getSession_returnsStateAndTranscript() throws Exception {
        SessionId id = SessionId.generate();
        InterviewSession session = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW));
        when(sessionApplicationService.getSession(id)).thenReturn(session);

        mockMvc.perform(get("/api/v1/sessions/{id}", id.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AWAITING_ANSWER"))
                .andExpect(jsonPath("$.transcript[0].role").value("INTERVIEWER"))
                .andExpect(jsonPath("$.transcript[0].content").value("Tell me about yourself."));
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/end returns 200 with the completed session")
    void endSession_returnsCompletedState() throws Exception {
        SessionId id = SessionId.generate();
        InterviewSession completed = InterviewSession.create(id)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", NOW))
                .apply(new SessionCommand.EndInterview());
        when(sessionApplicationService.endInterview(id)).thenReturn(completed);

        mockMvc.perform(post("/api/v1/sessions/{id}/end", id.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id.value().toString()))
                .andExpect(jsonPath("$.state").value("COMPLETED"));
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/end returns 409 when the session rejects the command")
    void endSession_whenSessionRejectsCommand_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionApplicationService.endInterview(any(SessionId.class)))
                .thenThrow(new SessionTransitionException(new SessionState.Created(), new SessionCommand.EndInterview()));

        mockMvc.perform(post("/api/v1/sessions/{id}/end", id))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/end returns 404 when the session does not exist")
    void endSession_whenNotFound_returns404() throws Exception {
        SessionId id = SessionId.generate();
        when(sessionApplicationService.endInterview(id)).thenThrow(new SessionNotFoundException(id));

        mockMvc.perform(post("/api/v1/sessions/{id}/end", id.value()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/cancel returns 200 with the cancelled session")
    void cancelSession_returnsCancelledState() throws Exception {
        SessionId id = SessionId.generate();
        InterviewSession cancelled = InterviewSession.create(id)
                .apply(new SessionCommand.CancelInterview());
        when(sessionApplicationService.cancelInterview(id)).thenReturn(cancelled);

        mockMvc.perform(post("/api/v1/sessions/{id}/cancel", id.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(id.value().toString()))
                .andExpect(jsonPath("$.state").value("CANCELLED"));
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/cancel returns 409 when the session rejects the command")
    void cancelSession_whenSessionRejectsCommand_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(sessionApplicationService.cancelInterview(any(SessionId.class)))
                .thenThrow(new SessionTransitionException(
                        new SessionState.Completed(), new SessionCommand.CancelInterview()));

        mockMvc.perform(post("/api/v1/sessions/{id}/cancel", id))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/sessions/{id}/cancel returns 404 when the session does not exist")
    void cancelSession_whenNotFound_returns404() throws Exception {
        SessionId id = SessionId.generate();
        when(sessionApplicationService.cancelInterview(id)).thenThrow(new SessionNotFoundException(id));

        mockMvc.perform(post("/api/v1/sessions/{id}/cancel", id.value()))
                .andExpect(status().isNotFound());
    }
}
