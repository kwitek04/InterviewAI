package com.interviewai.report.adapter.in.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Runtime settings for the interview-completed SQS worker.
 */
@ConfigurationProperties(prefix = "interviewai.report.worker")
record ReportWorkerProperties(
        boolean enabled,
        Duration claimLease,
        Duration visibilityExtensionInterval,
        String queueName,
        String dlqName,
        Duration longPollDuration,
        Duration visibilityTimeout,
        int maxReceiveCount) {

    ReportWorkerProperties {
        Objects.requireNonNull(claimLease, "claimLease must not be null");
        Objects.requireNonNull(visibilityExtensionInterval, "visibilityExtensionInterval must not be null");
        Objects.requireNonNull(queueName, "queueName must not be null");
        Objects.requireNonNull(dlqName, "dlqName must not be null");
        Objects.requireNonNull(longPollDuration, "longPollDuration must not be null");
        Objects.requireNonNull(visibilityTimeout, "visibilityTimeout must not be null");
        if (claimLease.isNegative() || claimLease.isZero()) {
            throw new IllegalArgumentException("claimLease must be positive");
        }
        if (visibilityExtensionInterval.isNegative() || visibilityExtensionInterval.isZero()) {
            throw new IllegalArgumentException("visibilityExtensionInterval must be positive");
        }
        if (queueName.isBlank()) {
            throw new IllegalArgumentException("queueName must not be blank");
        }
        if (dlqName.isBlank()) {
            throw new IllegalArgumentException("dlqName must not be blank");
        }
        if (longPollDuration.isNegative()) {
            throw new IllegalArgumentException("longPollDuration must not be negative");
        }
        if (visibilityTimeout.isNegative() || visibilityTimeout.isZero()) {
            throw new IllegalArgumentException("visibilityTimeout must be positive");
        }
        if (maxReceiveCount < 1) {
            throw new IllegalArgumentException("maxReceiveCount must be >= 1");
        }
    }
}
