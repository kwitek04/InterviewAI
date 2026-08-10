package com.interviewai.report.adapter.in.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically extends SQS visibility for an in-flight receipt handle.
 */
final class MessageVisibilityExtender implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MessageVisibilityExtender.class);

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final String receiptHandle;
    private final Duration visibilityTimeout;
    private final Duration extensionInterval;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ExecutorService executor;
    private final Future<?> future;

    MessageVisibilityExtender(
            SqsClient sqsClient,
            String queueUrl,
            String receiptHandle,
            Duration visibilityTimeout,
            Duration extensionInterval) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.receiptHandle = receiptHandle;
        this.visibilityTimeout = visibilityTimeout;
        this.extensionInterval = extensionInterval;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.future = executor.submit(this::extendLoop);
    }

    private void extendLoop() {
        while (active.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(extensionInterval);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!active.get()) {
                return;
            }
            try {
                sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(receiptHandle)
                        .visibilityTimeout((int) visibilityTimeout.toSeconds())
                        .build());
            } catch (RuntimeException exception) {
                log.warn("Failed to extend SQS visibility for receipt handle on {}", queueUrl, exception);
                return;
            }
        }
    }

    @Override
    public void close() {
        active.set(false);
        future.cancel(true);
        executor.shutdownNow();
    }
}
