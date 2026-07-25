package com.interviewai.session.adapter.out.messaging;

import com.interviewai.session.application.InterviewCompletedMessageMapper;
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
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
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
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class InterviewCompletedSqsRelayIT {

    private static final Instant QUESTION_TIME = Instant.parse("2026-01-01T10:00:00Z");
    private static final Duration MESSAGE_WAIT = Duration.ofSeconds(20);

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
        registry.add("interviewai.messaging.sqs.outbox-retry-interval", () -> "1h");
        registry.add("spring.modulith.events.staleness.processing", () -> "1h");
        registry.add("spring.modulith.events.staleness.published", () -> "1h");
        registry.add("spring.modulith.events.staleness.resubmitted", () -> "1h");
        registry.add("spring.modulith.events.staleness.check-interval", () -> "1h");
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

    @Autowired
    private FailedEventPublications failedEventPublications;

    @Autowired
    private IncompleteEventPublications incompleteEventPublications;

    @MockitoBean
    private CompletedInterviewPublisher completedInterviewPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            InterviewCompletedEvent event = invocation.getArgument(0);
            new SqsCompletedInterviewPublisher(sqsClient, properties).publish(event);
            return null;
        }).when(completedInterviewPublisher).publish(any());
        purgeQueue(properties.queueName());
        purgeQueue(properties.dlqName());
    }

    @Test
    @DisplayName("ending an interview delivers one SQS message with the versioned contract")
    void endInterview_deliversExactSqsContract() {
        SessionId sessionId = persistAwaitingAnswerSession();

        sessionApplicationService.endInterview(sessionId);

        Message message = awaitMessage(properties.queueName());
        assertThat(message.body()).contains(
                "\"eventType\":\"InterviewCompleted\"",
                "\"sessionId\":\"" + sessionId.value() + "\"",
                "\"schemaVersion\":1");
        assertThat(message.messageAttributes().get("contentType").stringValue())
                .isEqualTo(InterviewCompletedMessageMapper.CONTENT_TYPE_JSON);
        assertThat(message.messageAttributes().get("eventType").stringValue())
                .isEqualTo(InterviewCompletedMessageMapper.EVENT_TYPE);
        assertThat(message.messageAttributes().get("schemaVersion").stringValue()).isEqualTo("1");

        awaitUntil(() -> completedPublicationCount(sessionId) == 1);
    }

    @Test
    @DisplayName("a temporary SQS failure leaves the outbox incomplete and recovers after resubmission")
    void endInterview_recoversAfterTemporarySqsFailure() {
        SessionId sessionId = persistAwaitingAnswerSession();
        doThrow(new IllegalStateException("SQS unavailable"))
                .doAnswer(invocation -> {
                    InterviewCompletedEvent event = invocation.getArgument(0);
                    new SqsCompletedInterviewPublisher(sqsClient, properties).publish(event);
                    return null;
                })
                .when(completedInterviewPublisher).publish(any());

        sessionApplicationService.endInterview(sessionId);

        awaitUntil(() -> incompletePublicationCount(sessionId) == 1);
        assertThat(receiveOnce(properties.queueName())).isEmpty();

        failedEventPublications.resubmit(ResubmissionOptions.defaults().withBatchSize(10));

        Message message = awaitMessage(properties.queueName());
        assertThat(message.body()).contains("\"sessionId\":\"" + sessionId.value() + "\"");
        awaitUntil(() -> completedPublicationCount(sessionId) == 1);
    }

    @Test
    @DisplayName("a stale PROCESSING publication is recovered after simulated relay crash")
    void staleProcessingPublication_isRecovered() {
        SessionId sessionId = persistAwaitingAnswerSession();
        doThrow(new IllegalStateException("relay crashed"))
                .doAnswer(invocation -> {
                    InterviewCompletedEvent event = invocation.getArgument(0);
                    new SqsCompletedInterviewPublisher(sqsClient, properties).publish(event);
                    return null;
                })
                .when(completedInterviewPublisher).publish(any());

        sessionApplicationService.endInterview(sessionId);
        awaitUntil(() -> incompletePublicationCount(sessionId) == 1);

        jdbcClient.sql("""
                        UPDATE event_publication
                        SET status = 'PROCESSING',
                            completion_date = NULL,
                            publication_date = :staleAt
                        WHERE serialized_event LIKE :sessionMarker
                        """)
                .param("staleAt", java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))))
                .param("sessionMarker", "%" + sessionId.value() + "%")
                .update();

        assertThat(processingPublicationCount(sessionId)).isOne();

        incompleteEventPublications.resubmitIncompletePublications(
                ResubmissionOptions.defaults().withBatchSize(10));

        Message message = awaitMessage(properties.queueName());
        assertThat(message.body()).contains("\"sessionId\":\"" + sessionId.value() + "\"");
        awaitUntil(() -> completedPublicationCount(sessionId) == 1);
    }

    @Test
    @DisplayName("the interview-completed queue redrive policy points at the DLQ with the configured receive count")
    void queue_hasConfiguredRedrivePolicy() {
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName(properties.queueName())
                        .build())
                .queueUrl();
        String dlqArn = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                                        .queueName(properties.dlqName())
                                        .build())
                                .queueUrl())
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);

        String redrivePolicy = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.REDRIVE_POLICY)
                        .build())
                .attributes()
                .get(QueueAttributeName.REDRIVE_POLICY);

        assertThat(redrivePolicy).contains(dlqArn);
        assertThat(redrivePolicy).contains("\"maxReceiveCount\"");
        assertThat(redrivePolicy).contains(Integer.toString(properties.maxReceiveCount()));
    }

    private SessionId persistAwaitingAnswerSession() {
        SessionId sessionId = SessionId.generate();
        InterviewSession awaitingAnswer = InterviewSession.create(sessionId)
                .apply(new SessionCommand.StartInterview())
                .apply(new SessionCommand.AskQuestion("Tell me about yourself.", QUESTION_TIME));
        sessionRepository.save(awaitingAnswer);
        flushAndClear();
        return sessionId;
    }

    private void flushAndClear() {
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.flush();
            entityManager.clear();
        });
    }

    private Message awaitMessage(String queueName) {
        return awaitOptional(() -> receiveOnce(queueName).stream().findFirst())
                .orElseThrow(() -> new AssertionError("No SQS message received from " + queueName));
    }

    private List<Message> receiveOnce(String queueName) {
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
                .queueUrl();
        return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .messageAttributeNames("All")
                        .build())
                .messages();
    }

    private void purgeQueue(String queueName) {
        String queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
                .queueUrl();
        sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
    }

    private int completedPublicationCount(SessionId sessionId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM event_publication
                        WHERE serialized_event LIKE :sessionMarker
                          AND completion_date IS NOT NULL
                        """)
                .param("sessionMarker", "%" + sessionId.value() + "%")
                .query(Integer.class)
                .single();
    }

    private int incompletePublicationCount(SessionId sessionId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM event_publication
                        WHERE serialized_event LIKE :sessionMarker
                          AND completion_date IS NULL
                        """)
                .param("sessionMarker", "%" + sessionId.value() + "%")
                .query(Integer.class)
                .single();
    }

    private int processingPublicationCount(SessionId sessionId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM event_publication
                        WHERE serialized_event LIKE :sessionMarker
                          AND status = 'PROCESSING'
                        """)
                .param("sessionMarker", "%" + sessionId.value() + "%")
                .query(Integer.class)
                .single();
    }

    private void awaitUntil(Supplier<Boolean> condition) {
        awaitOptional(() -> condition.get() ? Optional.of(Boolean.TRUE) : Optional.empty());
    }

    private <T> Optional<T> awaitOptional(Supplier<Optional<T>> supplier) {
        long deadline = System.nanoTime() + MESSAGE_WAIT.toNanos();
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
                throw new IllegalStateException("Interrupted while waiting", interrupted);
            }
        }
        return current;
    }
}
