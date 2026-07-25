package com.interviewai.report.adapter.in.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Enables SQS consumer infrastructure only when the {@code worker} profile is active.
 */
@Configuration
@Profile("worker")
class WorkerSqsConsumerConfiguration {

    @Bean
    InterviewCompletedQueueConsumer interviewCompletedQueueConsumer() {
        return new InterviewCompletedQueueConsumer();
    }
}
