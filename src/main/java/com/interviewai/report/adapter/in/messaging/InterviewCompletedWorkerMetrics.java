package com.interviewai.report.adapter.in.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer counters for interview-completed worker outcomes.
 */
final class InterviewCompletedWorkerMetrics {

    private final Counter processed;
    private final Counter duplicate;
    private final Counter retried;
    private final Counter failed;
    private final Counter dlqBound;

    InterviewCompletedWorkerMetrics(MeterRegistry meterRegistry) {
        this.processed = Counter.builder("interviewai.report.worker.processed")
                .description("Interview-completed messages processed to a READY report")
                .register(meterRegistry);
        this.duplicate = Counter.builder("interviewai.report.worker.duplicate")
                .description("Interview-completed messages skipped as already completed")
                .register(meterRegistry);
        this.retried = Counter.builder("interviewai.report.worker.retried")
                .description("Interview-completed deliveries left for SQS redelivery")
                .register(meterRegistry);
        this.failed = Counter.builder("interviewai.report.worker.failed")
                .description("Interview-completed deliveries that failed generation")
                .register(meterRegistry);
        this.dlqBound = Counter.builder("interviewai.report.worker.dlq_bound")
                .description("Interview-completed deliveries that reached the redrive limit")
                .register(meterRegistry);
    }

    void processed() {
        processed.increment();
    }

    void duplicate() {
        duplicate.increment();
    }

    void retried() {
        retried.increment();
    }

    void failed() {
        failed.increment();
    }

    void dlqBound() {
        dlqBound.increment();
    }
}
