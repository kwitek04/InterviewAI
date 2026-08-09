package com.interviewai.session.adapter.out.messaging;

import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.application.port.out.CompletedInterviewPublisher;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.shared.InterviewCompletedEvent;
import com.interviewai.shared.SessionId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * Covers recovery that must happen on its own: the staleness monitor promoting abandoned
 * publications to FAILED and the scheduled resubmission draining them, with no operator
 * or test code triggering the Modulith resubmission APIs.
 */
@SpringBootTest
@Testcontainers
class InterviewCompletedOutboxRecoveryIT {

    private static final Instant QUESTION_TIME = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration RECOVERY_WAIT = Duration.ofSeconds(60);

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
        registry.add("spring.modulith.events.staleness.processing", () -> "2s");
        registry.add("spring.modulith.events.staleness.published", () -> "2s");
        registry.add("spring.modulith.events.staleness.resubmitted", () -> "2s");
        registry.add("spring.modulith.events.staleness.check-interval", () -> "1s");
    }

    @Autowired
    private SessionApplicationService sessionApplicationService;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private SqsMessagingProperties properties;

    @MockitoBean
    private CompletedInterviewPublisher completedInterviewPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    private OutboxSqsTestSupport outbox;

    @BeforeEach
    void setUp() {
        outbox = new OutboxSqsTestSupport(sqsClient, jdbcClient, RECOVERY_WAIT);
        outbox.purgeQueue(properties.queueName());
        outbox.purgeQueue(properties.dlqName());
    }

    @Test
    @DisplayName("a temporary broker outage is drained by the scheduled resubmission alone")
    void temporaryPublisherFailure_isRetriedByTheScheduler() {
        SessionId sessionId = persistAwaitingAnswerSession();
        doThrow(new IllegalStateException("SQS unavailable"))
                .doAnswer(invocation -> publishForReal(invocation.getArgument(0)))
                .when(completedInterviewPublisher).publish(any());

        sessionApplicationService.endInterview(sessionId);

        Message message = outbox.awaitMessage(properties.queueName());
        assertThat(message.body()).contains("\"sessionId\":\"" + sessionId.value() + "\"");
        outbox.awaitUntil(() -> outbox.completedPublicationCount(sessionId) == 1);
    }

    @Test
    @DisplayName("a publication abandoned in PROCESSING is recovered without operator action")
    void staleProcessingPublication_isRecoveredWithoutOperatorAction() {
        SessionId sessionId = persistAwaitingAnswerSession();
        doThrow(new IllegalStateException("relay crashed")).when(completedInterviewPublisher).publish(any());

        sessionApplicationService.endInterview(sessionId);
        outbox.awaitUntil(() -> outbox.incompletePublicationCount(sessionId) == 1);

        outbox.markStuckInProcessing(sessionId, Duration.ofMinutes(5));
        assertThat(outbox.processingPublicationCount(sessionId)).isOne();

        doAnswer(invocation -> publishForReal(invocation.getArgument(0)))
                .when(completedInterviewPublisher).publish(any());

        Message message = outbox.awaitMessage(properties.queueName());
        assertThat(message.body()).contains("\"sessionId\":\"" + sessionId.value() + "\"");
        outbox.awaitUntil(() -> outbox.completedPublicationCount(sessionId) == 1);
    }

    private Object publishForReal(InterviewCompletedEvent event) {
        new SqsCompletedInterviewPublisher(sqsClient, properties).publish(event);
        return null;
    }

    private SessionId persistAwaitingAnswerSession() {
        SessionId sessionId = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", QUESTION_TIME));
        sessionRepository.save(awaitingAnswer);
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.flush();
            entityManager.clear();
        });
        return sessionId;
    }
}
