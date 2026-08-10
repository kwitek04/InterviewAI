package com.interviewai.report.adapter.in.web;

import com.interviewai.report.domain.ReportStatus;
import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.shared.SessionId;
import com.interviewai.support.report.ControllableReportGenerator;
import com.interviewai.support.report.ControllableReportGeneratorConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end smoke: complete an interview, let the worker consume SQS, then read the report API.
 * Forces servlet web type so {@code RANDOM_PORT} works under the worker profile.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.web-application-type=servlet")
@ActiveProfiles("worker")
@Testcontainers
@Import(ControllableReportGeneratorConfiguration.class)
class ReportFlowSmokeIT {

    private static final Instant QUESTION_TIME = Instant.parse("2026-08-10T10:00:00Z");
    private static final Duration WAIT = Duration.ofSeconds(45);

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4"))
                    .withServices("s3", "sqs");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("interviewai.storage.s3.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("interviewai.storage.s3.region", LOCALSTACK::getRegion);
        registry.add("interviewai.storage.s3.access-key", LOCALSTACK::getAccessKey);
        registry.add("interviewai.storage.s3.secret-key", LOCALSTACK::getSecretKey);

        registry.add("interviewai.messaging.sqs.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("interviewai.messaging.sqs.region", LOCALSTACK::getRegion);
        registry.add("interviewai.messaging.sqs.access-key", LOCALSTACK::getAccessKey);
        registry.add("interviewai.messaging.sqs.secret-key", LOCALSTACK::getSecretKey);
        registry.add("interviewai.messaging.sqs.long-poll-duration", () -> "1s");
        registry.add("interviewai.messaging.sqs.visibility-timeout", () -> "30s");
        registry.add("interviewai.messaging.sqs.max-receive-count", () -> "3");
        registry.add("interviewai.messaging.sqs.outbox-retry-interval", () -> "1s");

        registry.add("interviewai.report.worker.enabled", () -> "true");
        registry.add("interviewai.report.worker.claim-lease", () -> "20s");
        registry.add("interviewai.report.worker.visibility-extension-interval", () -> "5s");
        registry.add("interviewai.report.worker.queue-name", () -> "interview-completed");
        registry.add("interviewai.report.worker.dlq-name", () -> "interview-completed-dlq");
        registry.add("interviewai.report.worker.long-poll-duration", () -> "1s");
        registry.add("interviewai.report.worker.visibility-timeout", () -> "30s");
        registry.add("interviewai.report.worker.max-receive-count", () -> "3");

        registry.add("spring.modulith.events.staleness.processing", () -> "2s");
        registry.add("spring.modulith.events.staleness.published", () -> "2s");
        registry.add("spring.modulith.events.staleness.resubmitted", () -> "2s");
        registry.add("spring.modulith.events.staleness.check-interval", () -> "1s");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionApplicationService sessionApplicationService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ControllableReportGenerator reportGenerator;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        reportGenerator.succeed();
        reportGenerator.resetInvocationCounts();
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName("interview-completed")
                        .build())
                .queueUrl();
        sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
    }

    @Test
    @DisplayName("ending an interview yields a READY report through SQS that the report API returns")
    void endInterview_workerProducesReportReadableViaApi() throws Exception {
        SessionId sessionId = persistAwaitingAnswerSession();
        sessionApplicationService.endInterview(sessionId);

        awaitUntil(() -> sessionRepository.findById(sessionId)
                .map(session -> session.state() instanceof SessionState.ReportReady)
                .orElse(false));

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/v1/sessions/" + sessionId.value() + "/report"))
                        .header("Accept", "application/json")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("status").asString()).isEqualTo(ReportStatus.READY.name());
        assertThat(body.get("sessionId").asString()).isEqualTo(sessionId.value().toString());
        assertThat(body.get("assessments").size()).isEqualTo(1);
        assertThat(body.get("strengths").isEmpty()).isFalse();
        assertThat(body.get("weaknesses").isEmpty()).isFalse();
        assertThat(body.get("recommendations").isEmpty()).isFalse();

        assertThat(reportGenerator.evaluateInvocations()).isEqualTo(1);
    }

    private SessionId persistAwaitingAnswerSession() {
        SessionId sessionId = SessionId.generate();
        InterviewSession awaiting = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", QUESTION_TIME))
                .apply(new SessionCommand.SubmitAnswer("I am a backend developer.", QUESTION_TIME.plusSeconds(1)))
                .apply(new SessionCommand.AskQuestion("Describe a hard bug.", QUESTION_TIME.plusSeconds(2)));
        sessionRepository.save(awaiting);
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.flush();
            entityManager.clear();
        });
        return sessionId;
    }

    private void awaitUntil(Supplier<Boolean> condition) {
        boolean ok = awaitOptional(() -> condition.get() ? Optional.of(Boolean.TRUE) : Optional.empty()).isPresent();
        if (!ok) {
            throw new AssertionError("Condition not met within " + WAIT);
        }
    }

    private <T> Optional<T> awaitOptional(Supplier<Optional<T>> supplier) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        Optional<T> current = Optional.empty();
        while (System.nanoTime() < deadline) {
            current = supplier.get();
            if (current.isPresent()) {
                return current;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
        return current;
    }
}
