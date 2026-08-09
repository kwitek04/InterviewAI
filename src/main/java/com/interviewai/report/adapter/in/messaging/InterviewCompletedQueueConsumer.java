package com.interviewai.report.adapter.in.messaging;

import com.interviewai.report.application.InterviewCompletedProcessingOutcome;
import com.interviewai.report.application.InterviewCompletedProcessingService;
import com.interviewai.shared.InterviewCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker-profile long-polling consumer for interview-completed SQS messages.
 * <p>
 * Acknowledges a message only after the application transaction that marks the
 * report ready and the processing claim completed has committed. No shutdown hook
 * deletes in-flight messages.
 */
public class InterviewCompletedQueueConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(InterviewCompletedQueueConsumer.class);

    private final SqsClient sqsClient;
    private final ReportWorkerProperties properties;
    private final InterviewCompletedProcessingService processingService;
    private final InterviewCompletedMessageParser parser;
    private final InterviewCompletedWorkerMetrics metrics;
    private final String workerId;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private ExecutorService pollExecutor;
    private volatile String queueUrl;

    InterviewCompletedQueueConsumer(
            SqsClient sqsClient,
            ReportWorkerProperties properties,
            InterviewCompletedProcessingService processingService,
            InterviewCompletedWorkerMetrics metrics) {
        this.sqsClient = sqsClient;
        this.properties = properties;
        this.processingService = processingService;
        this.parser = new InterviewCompletedMessageParser(JsonMapper.builder().build());
        this.metrics = metrics;
        this.workerId = "worker-" + UUID.randomUUID();
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    String workerId() {
        return workerId;
    }

    @Override
    public void start() {
        if (!properties.enabled()) {
            log.info("Interview-completed SQS consumer is disabled");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        shuttingDown.set(false);
        queueUrl = sqsClient.getQueueUrl(GetQueueUrlRequest.builder()
                        .queueName(properties.queueName())
                        .build())
                .queueUrl();
        pollExecutor = Executors.newVirtualThreadPerTaskExecutor();
        pollExecutor.execute(this::pollLoop);
        log.info(
                "Started interview-completed SQS consumer workerId={} queue={}",
                workerId,
                properties.queueName());
    }

    @Override
    public void stop() {
        stop(() -> {
        });
    }

    @Override
    public void stop(Runnable callback) {
        shuttingDown.set(true);
        running.set(false);
        ExecutorService executor = pollExecutor;
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            pollExecutor = null;
        }
        log.info("Stopped interview-completed SQS consumer workerId={}", workerId);
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return properties.enabled();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void pollLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .maxNumberOfMessages(1)
                                .waitTimeSeconds((int) properties.longPollDuration().toSeconds())
                                .visibilityTimeout((int) properties.visibilityTimeout().toSeconds())
                                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                                .messageAttributeNames("All")
                                .build())
                        .messages();
                for (Message message : messages) {
                    if (!running.get() || shuttingDown.get()) {
                        return;
                    }
                    handleMessage(message);
                }
            } catch (RuntimeException exception) {
                if (!running.get() || shuttingDown.get()) {
                    return;
                }
                log.warn("SQS poll failed for queue {}", properties.queueName(), exception);
                sleepQuietly(1_000);
            }
        }
    }

    private void handleMessage(Message message) {
        String messageId = message.messageId();
        int receiveCount = approximateReceiveCount(message);
        if (receiveCount > 1) {
            metrics.retried();
            log.info(
                    "SQS redelivery messageId={} receiveCount={} workerId={}",
                    messageId,
                    receiveCount,
                    workerId);
        }

        InterviewCompletedEvent event;
        try {
            event = parser.parse(message.body());
        } catch (MalformedInterviewCompletedMessageException exception) {
            metrics.failed();
            if (receiveCount >= properties.maxReceiveCount()) {
                metrics.dlqBound();
            }
            log.warn(
                    "Malformed interview-completed messageId={} receiveCount={} — leaving for SQS redrive",
                    messageId,
                    receiveCount,
                    exception);
            return;
        }

        log.info(
                "Handling interview-completed messageId={} eventId={} sessionId={} receiveCount={}",
                messageId,
                event.eventId(),
                event.sessionId().value(),
                receiveCount);

        try (MessageVisibilityExtender extender = new MessageVisibilityExtender(
                sqsClient,
                queueUrl,
                message.receiptHandle(),
                properties.visibilityTimeout(),
                properties.visibilityExtensionInterval())) {
            if (shuttingDown.get()) {
                return;
            }
            InterviewCompletedProcessingOutcome outcome = processingService.process(
                    event, workerId, receiveCount, properties.maxReceiveCount());
            if (shuttingDown.get()) {
                return;
            }
            acknowledge(outcome, message, receiveCount);
        } catch (RuntimeException exception) {
            metrics.failed();
            if (receiveCount >= properties.maxReceiveCount()) {
                metrics.dlqBound();
            }
            log.warn(
                    "Unhandled failure for messageId={} eventId={} — leaving for SQS redrive",
                    messageId,
                    event.eventId(),
                    exception);
        }
    }

    private void acknowledge(InterviewCompletedProcessingOutcome outcome, Message message, int receiveCount) {
        switch (outcome) {
            case COMPLETED -> {
                metrics.processed();
                deleteMessage(message.receiptHandle());
            }
            case DUPLICATE -> {
                metrics.duplicate();
                deleteMessage(message.receiptHandle());
            }
            case BUSY -> metrics.retried();
            case FAILED -> {
                metrics.failed();
                if (receiveCount >= properties.maxReceiveCount()) {
                    metrics.dlqBound();
                }
            }
        }
    }

    void deleteMessage(String receiptHandle) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build());
    }

    private static int approximateReceiveCount(Message message) {
        String raw = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
