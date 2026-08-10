package com.interviewai.report.adapter.in.web;

import com.interviewai.report.application.ReportFailedException;
import com.interviewai.report.application.ReportNotFoundException;
import com.interviewai.report.application.ReportQueryService;
import com.interviewai.report.application.ReportView;
import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.report.domain.ReportStatus;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReportControllerIT {

    private static final Instant GENERATED_AT = Instant.parse("2026-08-10T12:00:00Z");

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
    private ReportQueryService reportQueryService;

    @Test
    @DisplayName("GET report returns 202 with PENDING while generation has not started")
    void getReport_pending_returns202() throws Exception {
        SessionId sessionId = SessionId.generate();
        when(reportQueryService.getReport(sessionId))
                .thenReturn(new ReportView.InProgress(ReportStatus.PENDING.name()));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/report", sessionId.value()))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET report returns 202 with GENERATING while the worker is running")
    void getReport_generating_returns202() throws Exception {
        SessionId sessionId = SessionId.generate();
        when(reportQueryService.getReport(sessionId))
                .thenReturn(new ReportView.InProgress(ReportStatus.GENERATING.name()));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/report", sessionId.value()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("GENERATING"));
    }

    @Test
    @DisplayName("GET report returns 200 with the fixed ready contract")
    void getReport_ready_returnsExactContract() throws Exception {
        SessionId sessionId = SessionId.generate();
        InterviewReport ready = InterviewReport.pending(UUID.randomUUID(), sessionId, GENERATED_AT.minusSeconds(10))
                .markGenerating(GENERATED_AT.minusSeconds(5))
                .markReady(
                        List.of(new QuestionAssessment(
                                0, "Tell me about yourself", "I am a backend developer", 4, "Solid")),
                        List.of("Clear communicator"),
                        List.of("Needs deeper examples"),
                        List.of("Prepare STAR stories"),
                        GENERATED_AT);
        when(reportQueryService.getReport(sessionId)).thenReturn(new ReportView.Ready(ready));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/report", sessionId.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.value().toString()))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.generatedAt").value(GENERATED_AT.toString()))
                .andExpect(jsonPath("$.assessments").isArray())
                .andExpect(jsonPath("$.assessments.length()").value(1))
                .andExpect(jsonPath("$.assessments[0].questionIndex").value(0))
                .andExpect(jsonPath("$.assessments[0].question").value("Tell me about yourself"))
                .andExpect(jsonPath("$.assessments[0].answer").value("I am a backend developer"))
                .andExpect(jsonPath("$.assessments[0].score").value(4))
                .andExpect(jsonPath("$.assessments[0].scoreLabel").value("Strong"))
                .andExpect(jsonPath("$.assessments[0].rationale").value("Solid"))
                .andExpect(jsonPath("$.strengths[0]").value("Clear communicator"))
                .andExpect(jsonPath("$.weaknesses[0]").value("Needs deeper examples"))
                .andExpect(jsonPath("$.recommendations[0]").value("Prepare STAR stories"));
    }

    @Test
    @DisplayName("GET report returns 404 when the session has no report workflow")
    void getReport_unknownSession_returns404() throws Exception {
        SessionId sessionId = SessionId.generate();
        when(reportQueryService.getReport(any())).thenThrow(new ReportNotFoundException(sessionId));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/report", sessionId.value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Report not found for session " + sessionId.value()));
    }

    @Test
    @DisplayName("GET report returns a safe RFC 7807 body for terminal failure")
    void getReport_failed_returnsSafeProblemDetail() throws Exception {
        SessionId sessionId = SessionId.generate();
        when(reportQueryService.getReport(sessionId))
                .thenThrow(new ReportFailedException("Report generation did not complete within the allowed number of attempts."));

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/report", sessionId.value()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Report generation failed"))
                .andExpect(jsonPath("$.detail").value(
                        "Report generation did not complete within the allowed number of attempts."));
    }
}
