package com.interviewai.session.adapter.out.messaging;

import com.interviewai.shared.SessionId;
import org.springframework.jdbc.core.simple.JdbcClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Shared SQS and event-publication queries for outbox integration tests.
 */
final class OutboxSqsTestSupport {

    private final SqsClient sqsClient;
    private final JdbcClient jdbcClient;
    private final Duration timeout;

    OutboxSqsTestSupport(SqsClient sqsClient, JdbcClient jdbcClient, Duration timeout) {
        this.sqsClient = sqsClient;
        this.jdbcClient = jdbcClient;
        this.timeout = timeout;
    }

    Message awaitMessage(String queueName) {
        return awaitMessageContaining(queueName, null);
    }

    Message awaitMessageContaining(String queueName, String bodyMarker) {
        return awaitOptional(() -> {
            for (Message message : receiveOnce(queueName)) {
                deleteMessage(queueUrl(queueName), message);
                if (bodyMarker == null || message.body().contains(bodyMarker)) {
                    return Optional.of(message);
                }
            }
            return Optional.empty();
        }).orElseThrow(() -> new AssertionError("No SQS message received from " + queueName
                + (bodyMarker == null ? "" : " containing " + bodyMarker)));
    }

    List<Message> receiveOnce(String queueName) {
        return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl(queueName))
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .visibilityTimeout(5)
                        .messageAttributeNames("All")
                        .build())
                .messages();
    }

    /**
     * Drains the queue by repeatedly receiving and deleting messages.
     * Prefer this over {@code PurgeQueue}, which is rate-limited to once per 60 seconds.
     */
    void drainQueue(String queueName) {
        String queueUrl = queueUrl(queueName);
        for (int attempt = 0; attempt < 20; attempt++) {
            List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(0)
                            .visibilityTimeout(30)
                            .build())
                    .messages();
            if (messages.isEmpty()) {
                return;
            }
            for (Message message : messages) {
                deleteMessage(queueUrl, message);
            }
        }
    }

    void purgeQueue(String queueName) {
        drainQueue(queueName);
    }

    String queueUrl(String queueName) {
        return sqsClient.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
    }

    int completedPublicationCount(SessionId sessionId) {
        return count(sessionId, """
                SELECT COUNT(*) FROM event_publication
                WHERE serialized_event LIKE :sessionMarker AND completion_date IS NOT NULL
                """);
    }

    int incompletePublicationCount(SessionId sessionId) {
        return count(sessionId, """
                SELECT COUNT(*) FROM event_publication
                WHERE serialized_event LIKE :sessionMarker AND completion_date IS NULL
                """);
    }

    int processingPublicationCount(SessionId sessionId) {
        return count(sessionId, """
                SELECT COUNT(*) FROM event_publication
                WHERE serialized_event LIKE :sessionMarker AND status = 'PROCESSING'
                """);
    }

    /**
     * Rewrites the publication into the state a relay crash leaves behind: claimed for
     * processing, never completed, and old enough for the staleness monitor to notice.
     */
    void markStuckInProcessing(SessionId sessionId, Duration age) {
        jdbcClient.sql("""
                        UPDATE event_publication
                        SET status = 'PROCESSING',
                            completion_date = NULL,
                            publication_date = :staleAt
                        WHERE serialized_event LIKE :sessionMarker
                        """)
                .param("staleAt", Timestamp.from(Instant.now().minus(age)))
                .param("sessionMarker", sessionMarker(sessionId))
                .update();
    }

    /**
     * Promotes a stuck publication to {@code FAILED}, matching what the staleness monitor
     * does before scheduled resubmission can deliver it again.
     */
    void markFailed(SessionId sessionId) {
        jdbcClient.sql("""
                        UPDATE event_publication
                        SET status = 'FAILED',
                            completion_date = NULL
                        WHERE serialized_event LIKE :sessionMarker
                          AND completion_date IS NULL
                        """)
                .param("sessionMarker", sessionMarker(sessionId))
                .update();
    }

    void awaitUntil(Supplier<Boolean> condition) {
        boolean satisfied = awaitOptional(() -> condition.get() ? Optional.of(Boolean.TRUE) : Optional.empty())
                .isPresent();
        if (!satisfied) {
            throw new AssertionError("Condition was not satisfied within " + timeout);
        }
    }

    private void deleteMessage(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }

    private int count(SessionId sessionId, String sql) {
        return jdbcClient.sql(sql)
                .param("sessionMarker", sessionMarker(sessionId))
                .query(Integer.class)
                .single();
    }

    private static String sessionMarker(SessionId sessionId) {
        return "%" + sessionId.value() + "%";
    }

    private <T> Optional<T> awaitOptional(Supplier<Optional<T>> supplier) {
        long deadline = System.nanoTime() + timeout.toNanos();
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
