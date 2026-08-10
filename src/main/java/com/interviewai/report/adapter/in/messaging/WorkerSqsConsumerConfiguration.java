package com.interviewai.report.adapter.in.messaging;

import com.interviewai.report.application.InterviewCompletedProcessingService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Enables SQS consumer infrastructure only when the {@code worker} profile is active.
 */
@Configuration
@Profile("worker")
@EnableConfigurationProperties(ReportWorkerProperties.class)
class WorkerSqsConsumerConfiguration {

    @Bean
    InterviewCompletedWorkerMetrics interviewCompletedWorkerMetrics(MeterRegistry meterRegistry) {
        return new InterviewCompletedWorkerMetrics(meterRegistry);
    }

    @Bean
    InterviewCompletedQueueConsumer interviewCompletedQueueConsumer(
            SqsClient sqsClient,
            ReportWorkerProperties properties,
            InterviewCompletedProcessingService processingService,
            InterviewCompletedWorkerMetrics metrics) {
        return new InterviewCompletedQueueConsumer(sqsClient, properties, processingService, metrics);
    }
}
