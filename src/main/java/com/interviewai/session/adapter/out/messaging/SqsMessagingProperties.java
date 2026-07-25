package com.interviewai.session.adapter.out.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Connection and queue settings for SQS-based interview completion messaging.
 */
@ConfigurationProperties(prefix = "interviewai.messaging.sqs")
record SqsMessagingProperties(
        URI endpoint,
        String region,
        String accessKey,
        String secretKey,
        String queueName,
        String dlqName,
        Duration longPollDuration,
        Duration visibilityTimeout,
        int maxReceiveCount,
        Duration outboxRetryInterval,
        int outboxResubmissionBatchSize) {
}
