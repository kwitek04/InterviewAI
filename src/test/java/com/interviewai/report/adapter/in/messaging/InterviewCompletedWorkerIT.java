package com.interviewai.report.adapter.in.messaging;

import com.interviewai.report.application.port.out.ReportRepository;
import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.ProcessedEventStatus;
import com.interviewai.report.domain.ReportStatus;
import com.interviewai.session.application.InterviewCompletedMessageMapper;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.shared.InterviewCompletedEvent;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
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
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("worker")
@Testcontainers
@Import(ControllableReportGeneratorConfiguration.class)
class InterviewCompletedWorkerIT {

    private static final Instant QUESTION_TIME = Instant.parse("2026-08-09T10:00:00Z");
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
        registry.add("interviewai.messaging.sqs.visibility-timeout", () -> "5s");
        registry.add("interviewai.messaging.sqs.max-receive-count", () -> "3");
        registry.add("interviewai.messaging.sqs.outbox-retry-interval", () -> "1h");

        registry.add("interviewai.report.worker.enabled", () -> "true");
        registry.add("interviewai.report.worker.claim-lease", () -> "3s");
        registry.add("interviewai.report.worker.visibility-extension-interval", () -> "1s");
        registry.add("interviewai.report.worker.queue-name", () -> "interview-completed");
        registry.add("interviewai.report.worker.dlq-name", () -> "interview-completed-dlq");
        registry.add("interviewai.report.worker.long-poll-duration", () -> "1s");
        registry.add("interviewai.report.worker.visibility-timeout", () -> "5s");
        registry.add("interviewai.report.worker.max-receive-count", () -> "3");

        registry.add("spring.modulith.events.staleness.processing", () -> "1h");
        registry.add("spring.modulith.events.staleness.published", () -> "1h");
        registry.add("spring.modulith.events.staleness.resubmitted", () -> "1h");
        registry.add("spring.modulith.events.staleness.check-interval", () -> "1h");
    }

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ControllableReportGenerator reportGenerator;

    @Autowired
    private InterviewCompletedQueueConsumer consumer;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private String queueUrl;
    private String dlqUrl;

    @BeforeEach
    void setUp() {
        reportGenerator.succeed();
        reportGenerator.resetInvocationCounts();
        if (!consumer.isRunning()) {
            consumer.start();
        }
        queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName("interview-completed")
                        .build())
                .queueUrl();
        dlqUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName("interview-completed-dlq")
                        .build())
                .queueUrl();
        purge(queueUrl);
        purge(dlqUrl);
    }

    @Test
    @DisplayName("a completion event yields REPORT_READY, one READY report, and one completed claim")
    void happyPath_producesReadyReportAndDeletesMessage() {
        SessionId sessionId = persistCompletedInterview();
        InterviewCompletedEvent event = new InterviewCompletedEvent(
                UUID.randomUUID(), sessionId, Instant.parse("2026-08-09T11:00:00Z"));
        enqueue(event);

        awaitUntil(() -> reportRepository.findBySessionId(sessionId)
                .map(InterviewReport::isReady)
                .orElse(false));

        InterviewReport report = reportRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.assessments()).hasSize(1);
        assertThat(sessionRepository.findById(sessionId)).get()
                .extracting(InterviewSession::state)
                .isEqualTo(new SessionState.ReportReady());
        assertThat(completedClaimCount(event.eventId())).isOne();
        assertThat(reportGenerator.evaluateInvocations()).isEqualTo(1);
        assertThat(awaitOptional(() -> receiveOnce(queueUrl).stream().findFirst())).isEmpty();
    }

    @Test
    @DisplayName("a duplicate eventId after completion does not invoke the LLM again")
    void duplicateDelivery_afterCompletion_skipsLlmAndDeletes() {
        SessionId sessionId = persistCompletedInterview();
        InterviewCompletedEvent event = new InterviewCompletedEvent(
                UUID.randomUUID(), sessionId, Instant.parse("2026-08-09T11:00:00Z"));
        enqueue(event);
        awaitUntil(() -> reportRepository.findBySessionId(sessionId).map(InterviewReport::isReady).orElse(false));
        int evaluations = reportGenerator.evaluateInvocations();

        enqueue(event);
        awaitUntil(() -> awaitOptional(() -> receiveOnce(queueUrl).stream().findFirst()).isEmpty());

        assertThat(reportGenerator.evaluateInvocations()).isEqualTo(evaluations);
        assertThat(reportRepository.findBySessionId(sessionId)).get()
                .extracting(InterviewReport::status)
                .isEqualTo(ReportStatus.READY);
        assertThat(completedClaimCount(event.eventId())).isOne();
    }

    @Test
    @DisplayName("concurrent deliveries with the same eventId start the LLM only once")
    void concurrentDelivery_onlyOneLlmInvocation() {
        SessionId sessionId = persistCompletedInterview();
        InterviewCompletedEvent event = new InterviewCompletedEvent(
                UUID.randomUUID(), sessionId, Instant.parse("2026-08-09T11:00:00Z"));
        reportGenerator.blockEvaluate();

        enqueue(event);
        enqueue(event);

        awaitUntil(() -> reportGenerator.evaluateInvocations() >= 1);
        sleep(Duration.ofSeconds(2));
        assertThat(reportGenerator.evaluateInvocations()).isEqualTo(1);

        reportGenerator.releaseEvaluate();
        awaitUntil(() -> reportRepository.findBySessionId(sessionId).map(InterviewReport::isReady).orElse(false));
        assertThat(reportGenerator.evaluateInvocations()).isEqualTo(1);
    }

    @Test
    @DisplayName("a crash before completion allows reclaim after visibility timeout")
    void crashBeforeCompletion_redeliversAndCompletes() {
        SessionId sessionId = persistCompletedInterview();
        InterviewCompletedEvent event = new InterviewCompletedEvent(
                UUID.randomUUID(), sessionId, Instant.parse("2026-08-09T11:00:00Z"));
        reportGenerator.blockEvaluate();
        enqueue(event);

        awaitUntil(() -> reportGenerator.evaluateInvocations() >= 1);
        consumer.stop();
        assertThat(reportRepository.findBySessionId(sessionId).map(InterviewReport::isReady).orElse(false))
                .isFalse();

        sleep(Duration.ofSeconds(6));
        reportGenerator.succeed();
        reportGenerator.releaseEvaluate();
        consumer.start();

        awaitUntil(() -> reportRepository.findBySessionId(sessionId).map(InterviewReport::isReady).orElse(false));
        assertThat(sessionRepository.findById(sessionId)).get()
                .extracting(InterviewSession::state)
                .isEqualTo(new SessionState.ReportReady());
        assertThat(completedClaimCount(event.eventId())).isOne();
        assertThat(reportGenerator.evaluateInvocations()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("redelivery after commit but before delete acknowledges without calling the LLM")
    void postCommitPreDelete_redeliveryDeletesWithoutLlm() {
        SessionId sessionId = persistCompletedInterview();
        InterviewCompletedEvent event = new InterviewCompletedEvent(
                UUID.randomUUID(), sessionId, Instant.parse("2026-08-09T11:00:00Z"));
        enqueue(event);
        awaitUntil(() -> reportRepository.findBySessionId(sessionId).map(InterviewReport::isReady).orElse(false));
        int evaluations = reportGenerator.evaluateInvocations();

        enqueue(event);
        awaitUntil(() -> awaitOptional(() -> receiveOnce(queueUrl).stream().findFirst()).isEmpty());

        assertThat(reportGenerator.evaluateInvocations()).isEqualTo(evaluations);
        assertThat(completedClaimCount(event.eventId())).isOne();
    }

    @Test
    @DisplayName("exhausted generation failures move the message to the DLQ and leave a FAILED report")
    void generationAlwaysFails_messageLandsInDlq() {
        SessionId sessionId = persistCompletedInterview();
        InterviewCompletedEvent event = new InterviewCompletedEvent(
                UUID.randomUUID(), sessionId, Instant.parse("2026-08-09T11:00:00Z"));
        reportGenerator.failEvaluate();
        enqueue(event);

        Message dlqMessage = awaitMessage(dlqUrl);
        assertThat(dlqMessage.body()).contains(event.eventId().toString());
        assertThat(awaitOptional(() -> receiveOnce(queueUrl).stream().findFirst())).isEmpty();

        InterviewReport report = reportRepository.findBySessionId(sessionId).orElseThrow();
        assertThat(report.status()).isEqualTo(ReportStatus.FAILED);
        assertThat(report.failureMessage()).isNotBlank();
    }

    @Test
    @DisplayName("visibility extension keeps a slow generation exclusive")
    void visibilityExtension_preventsConcurrentProcessing() {
        SessionId sessionId = persistCompletedInterview();
        InterviewCompletedEvent event = new InterviewCompletedEvent(
                UUID.randomUUID(), sessionId, Instant.parse("2026-08-09T11:00:00Z"));
        reportGenerator.delayEvaluate(8_000);
        enqueue(event);

        awaitUntil(() -> reportGenerator.evaluateInvocations() >= 1);
        sleep(Duration.ofSeconds(7));
        assertThat(reportGenerator.evaluateInvocations()).isEqualTo(1);

        awaitUntil(() -> reportRepository.findBySessionId(sessionId).map(InterviewReport::isReady).orElse(false));
        assertThat(reportGenerator.evaluateInvocations()).isEqualTo(1);
    }

    private SessionId persistCompletedInterview() {
        SessionId sessionId = SessionId.generate();
        InterviewSession completed = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", QUESTION_TIME))
                .apply(new SessionCommand.SubmitAnswer("I am a backend developer.", QUESTION_TIME.plusSeconds(1)))
                .apply(new SessionCommand.AskQuestion("Describe a hard bug.", QUESTION_TIME.plusSeconds(2)))
                .apply(new SessionCommand.EndInterview());
        sessionRepository.save(completed);
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.flush();
            entityManager.clear();
        });
        return sessionId;
    }

    private void enqueue(InterviewCompletedEvent event) {
        Map<String, MessageAttributeValue> attributes = new LinkedHashMap<>();
        InterviewCompletedMessageMapper.toMessageAttributes(event).forEach((name, value) ->
                attributes.put(name, MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(value)
                        .build()));
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(InterviewCompletedMessageMapper.toJsonBody(event))
                .messageAttributes(attributes)
                .build());
    }

    private int completedClaimCount(UUID eventId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM processed_event
                        WHERE event_id = :eventId AND status = :status
                        """)
                .param("eventId", eventId)
                .param("status", ProcessedEventStatus.COMPLETED.name())
                .query(Integer.class)
                .single();
    }

    private Message awaitMessage(String targetQueueUrl) {
        return awaitOptional(() -> receiveOnce(targetQueueUrl).stream().findFirst())
                .orElseThrow(() -> new AssertionError("No SQS message received"));
    }

    private List<Message> receiveOnce(String targetQueueUrl) {
        return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(targetQueueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .messageAttributeNames("All")
                        .build())
                .messages();
    }

    private void purge(String targetQueueUrl) {
        sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(targetQueueUrl).build());
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
            sleep(Duration.ofMillis(200));
        }
        return current;
    }

    private static void sleep(Duration duration) {
        try {
            TimeUnit.MILLISECONDS.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
