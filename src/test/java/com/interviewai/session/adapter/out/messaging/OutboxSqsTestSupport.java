package com.interviewai.session.adapter.out.messaging;

import com.interviewai.shared.SessionId;
import org.springframework.jdbc.core.simple.JdbcClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
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
        return awaitOptional(() -> receiveOnce(queueName).stream().findFirst())
                .orElseThrow(() -> new AssertionError("No SQS message received from " + queueName));
    }

    List<Message> receiveOnce(String queueName) {
        return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl(queueName))
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(1)
                        .messageAttributeNames("All")
                        .build())
                .messages();
    }

    void purgeQueue(String queueName) {
        sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl(queueName)).build());
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

    void awaitUntil(Supplier<Boolean> condition) {
        boolean satisfied = awaitOptional(() -> condition.get() ? Optional.of(Boolean.TRUE) : Optional.empty())
                .isPresent();
        if (!satisfied) {
            throw new AssertionError("Condition was not satisfied within " + timeout);
        }
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
