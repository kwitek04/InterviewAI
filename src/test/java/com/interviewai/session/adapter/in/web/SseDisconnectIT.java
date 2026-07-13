package com.interviewai.session.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.interview.domain.QuestionResponseStatus;
import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;
import com.interviewai.support.streaming.ControllableStreamingQuestionGenerator;
import com.interviewai.support.streaming.ControllableStreamingQuestionGeneratorConfiguration;
import com.interviewai.support.streaming.RestTestClient;
import com.interviewai.support.streaming.SseTestClient;
import com.interviewai.support.streaming.SseTestClient.ReceivedSseEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(ControllableStreamingQuestionGeneratorConfiguration.class)
class SseDisconnectIT {

    private static final String SAFE_FAILURE_MESSAGE = "Question generation failed.";
    private static final List<String> PARTIAL_TOKENS = List.of("How", " did", " you", " use", " Spring", " Boot?");
    private static final String FINAL_QUESTION = "How did you use Spring Boot?";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("interviewai.streaming.poll-interval", () -> "20ms");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ControllableStreamingQuestionGenerator streamingQuestionGenerator;

    @Autowired
    private QuestionResponseStore questionResponseStore;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTestClient restTestClient;
    private SseTestClient sseTestClient;

    @BeforeEach
    void setUp() {
        restTestClient = new RestTestClient(port, objectMapper);
        sseTestClient = new SseTestClient(port);
    }

    @Test
    @DisplayName("mid-stream disconnect still completes generation and reconnect replays missing events")
    void disconnectMidStream_reconnectReceivesMissingEventsAndPersistsSingleTranscriptMessage() throws Exception {
        streamingQuestionGenerator.configure(PARTIAL_TOKENS, FINAL_QUESTION, 2);

        JsonNode startResponse = restTestClient.startSession();
        UUID sessionId = UUID.fromString(startResponse.get("sessionId").asText());
        String eventsUrl = startResponse.get("eventsUrl").asText();

        String secondEventId;
        try (SseTestClient.SseStream stream = sseTestClient.openStream(eventsUrl, null)) {
            ReceivedSseEvent firstToken = stream.readNextEvent(Duration.ofSeconds(10));
            ReceivedSseEvent secondToken = stream.readNextEvent(Duration.ofSeconds(10));

            assertThat(firstToken.eventName()).isEqualTo("token");
            assertThat(readTokenText(firstToken)).isEqualTo("How");
            assertThat(secondToken.eventName()).isEqualTo("token");
            assertThat(readTokenText(secondToken)).isEqualTo(" did");

            secondEventId = secondToken.id();
        }

        streamingQuestionGenerator.releasePausedGeneration();

        JsonNode session = awaitAwaitingAnswer(sessionId);
        assertThat(session.get("transcript")).hasSize(1);
        assertThat(session.get("transcript").get(0).get("role").asText()).isEqualTo("INTERVIEWER");
        assertThat(session.get("transcript").get(0).get("content").asText()).isEqualTo(FINAL_QUESTION);

        List<ReceivedSseEvent> replayedEvents;
        try (SseTestClient.SseStream replay = sseTestClient.openStream(eventsUrl, secondEventId)) {
            replayedEvents = replay.readEventsUntilTerminal(Duration.ofSeconds(10), 10);
        }

        assertThat(replayedEvents).hasSize(5);
        assertThat(replayedEvents.subList(0, 4)).allSatisfy(event -> assertThat(event.eventName()).isEqualTo("token"));
        assertThat(replayedEvents.getLast().eventName()).isEqualTo("completed");
        assertThat(readQuestion(replayedEvents.getLast())).isEqualTo(FINAL_QUESTION);

        assertThat(replayedEvents.stream().map(ReceivedSseEvent::id).toList())
                .containsExactly("3", "4", "5", "6", "7");
        assertThat(replayedEvents.stream().map(ReceivedSseEvent::id).collect(Collectors.toSet()))
                .hasSize(replayedEvents.size());

        List<String> replayedTokenTexts = replayedEvents.stream()
                .filter(event -> "token".equals(event.eventName()))
                .map(this::readTokenText)
                .toList();
        assertThat(String.join("", replayedTokenTexts)).isEqualTo(" you use Spring Boot?");
        assertThat(readQuestion(replayedEvents.getLast()))
                .isEqualTo(session.get("transcript").get(0).get("content").asText());
    }

    @Test
    @DisplayName("two SSE subscribers can read one response independently")
    void twoSubscribers_canReadSameResponseWithoutInterference() throws Exception {
        streamingQuestionGenerator.configure(PARTIAL_TOKENS, FINAL_QUESTION, 0);

        JsonNode startResponse = restTestClient.startSession();
        String eventsUrl = startResponse.get("eventsUrl").asText();

        try (SseTestClient.SseStream first = sseTestClient.openStream(eventsUrl, null);
                SseTestClient.SseStream second = sseTestClient.openStream(eventsUrl, null)) {
            List<ReceivedSseEvent> firstEvents = first.readEventsUntilTerminal(Duration.ofSeconds(10), 20);
            List<ReceivedSseEvent> secondEvents = second.readEventsUntilTerminal(Duration.ofSeconds(10), 20);

            assertThat(tokenTexts(firstEvents)).isEqualTo(PARTIAL_TOKENS);
            assertThat(tokenTexts(secondEvents)).isEqualTo(PARTIAL_TOKENS);
            assertThat(firstEvents.getLast().eventName()).isEqualTo("completed");
            assertThat(secondEvents.getLast().eventName()).isEqualTo("completed");
        }
    }

    @Test
    @DisplayName("disconnecting all subscribers still allows generation to complete")
    void disconnectingAllSubscribers_generationStillCompletes() throws Exception {
        streamingQuestionGenerator.configure(PARTIAL_TOKENS, FINAL_QUESTION, 2);

        JsonNode startResponse = restTestClient.startSession();
        UUID sessionId = UUID.fromString(startResponse.get("sessionId").asText());
        String eventsUrl = startResponse.get("eventsUrl").asText();

        try (SseTestClient.SseStream stream = sseTestClient.openStream(eventsUrl, null)) {
            stream.readNextEvent(Duration.ofSeconds(10));
            stream.readNextEvent(Duration.ofSeconds(10));
        }

        streamingQuestionGenerator.releasePausedGeneration();

        JsonNode session = awaitAwaitingAnswer(sessionId);
        assertThat(session.get("transcript")).hasSize(1);
        assertThat(session.get("transcript").get(0).get("content").asText()).isEqualTo(FINAL_QUESTION);
    }

    @Test
    @DisplayName("duplicate answer submission while generation is active is rejected")
    void duplicateSubmitWhileGenerating_isRejected() throws Exception {
        streamingQuestionGenerator.configure(PARTIAL_TOKENS, FINAL_QUESTION, 2);

        JsonNode startResponse = restTestClient.startSession();
        UUID sessionId = UUID.fromString(startResponse.get("sessionId").asText());

        try (SseTestClient.SseStream stream = sseTestClient.openStream(startResponse.get("eventsUrl").asText(), null)) {
            stream.readNextEvent(Duration.ofSeconds(10));

            assertThat(restTestClient.submitAnswer(sessionId, "Too early")).isEqualTo(409);
        }

        streamingQuestionGenerator.releasePausedGeneration();
        awaitAwaitingAnswer(sessionId);

        assertThat(questionResponseStore.findActiveBySessionId(new SessionId(sessionId))).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("completed response replay works after persistence context is cleared")
    void completedResponseReplay_worksAfterPersistenceContextCleared() throws Exception {
        streamingQuestionGenerator.configure(PARTIAL_TOKENS, FINAL_QUESTION, 0);

        JsonNode startResponse = restTestClient.startSession();
        UUID sessionId = UUID.fromString(startResponse.get("sessionId").asText());
        String eventsUrl = startResponse.get("eventsUrl").asText();

        awaitAwaitingAnswer(sessionId);

        entityManager.flush();
        entityManager.clear();

        List<ReceivedSseEvent> replayedEvents;
        try (SseTestClient.SseStream replay = sseTestClient.openStream(eventsUrl, null)) {
            replayedEvents = replay.readEventsUntilTerminal(Duration.ofSeconds(10), 20);
        }

        assertThat(tokenTexts(replayedEvents)).isEqualTo(PARTIAL_TOKENS);
        assertThat(replayedEvents.getLast().eventName()).isEqualTo("completed");
        assertThat(readQuestion(replayedEvents.getLast())).isEqualTo(FINAL_QUESTION);
    }

    @Test
    @DisplayName("generation failure emits one terminal error and leaves transcript without interviewer message")
    void generationFailure_emitsTerminalErrorWithoutPartialTranscriptMessage() throws Exception {
        streamingQuestionGenerator.configure(PARTIAL_TOKENS, FINAL_QUESTION, 0);
        streamingQuestionGenerator.failNextGeneration();

        JsonNode startResponse = restTestClient.startSession();
        UUID sessionId = UUID.fromString(startResponse.get("sessionId").asText());
        UUID responseId = UUID.fromString(startResponse.get("responseId").asText());
        String eventsUrl = startResponse.get("eventsUrl").asText();

        List<ReceivedSseEvent> events;
        try (SseTestClient.SseStream stream = sseTestClient.openStream(eventsUrl, null)) {
            events = stream.readEventsUntilTerminal(Duration.ofSeconds(10), 5);
        }

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().eventName()).isEqualTo("error");
        assertThat(readErrorMessage(events.getFirst())).isEqualTo(SAFE_FAILURE_MESSAGE);

        JsonNode session = restTestClient.fetchSessionJson(sessionId);
        assertThat(session.get("state").asText()).isEqualTo("IN_PROGRESS");
        assertThat(session.get("transcript")).isEmpty();

        assertThat(questionResponseStore.findById(new ResponseId(responseId)))
                .isPresent()
                .get()
                .extracting(response -> response.status())
                .isEqualTo(QuestionResponseStatus.FAILED);
    }

    private JsonNode awaitAwaitingAnswer(UUID sessionId) throws Exception {
        for (int attempt = 0; attempt < 200; attempt++) {
            JsonNode session = restTestClient.fetchSessionJson(sessionId);
            if ("AWAITING_ANSWER".equals(session.get("state").asText())) {
                return session;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Timed out waiting for AWAITING_ANSWER for session " + sessionId);
    }

    private String readTokenText(ReceivedSseEvent event) {
        try {
            return objectMapper.readTree(event.data()).get("text").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse token payload: " + event.data(), exception);
        }
    }

    private String readQuestion(ReceivedSseEvent event) {
        try {
            return objectMapper.readTree(event.data()).get("question").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse completed payload: " + event.data(), exception);
        }
    }

    private String readErrorMessage(ReceivedSseEvent event) {
        try {
            return objectMapper.readTree(event.data()).get("message").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse error payload: " + event.data(), exception);
        }
    }

    private List<String> tokenTexts(List<ReceivedSseEvent> events) {
        return events.stream()
                .filter(event -> "token".equals(event.eventName()))
                .map(this::readTokenText)
                .toList();
    }
}
