package com.interviewai.session.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically resubmits failed transactional event publications so temporary broker
 * outages and stale processing attempts are retried with a bounded batch size.
 */
@Component
public class FailedPublicationRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FailedPublicationRecoveryScheduler.class);

    private final FailedEventPublications failedEventPublications;
    private final int batchSize;

    public FailedPublicationRecoveryScheduler(
            FailedEventPublications failedEventPublications,
            @Value("${interviewai.messaging.sqs.outbox-resubmission-batch-size}") int batchSize) {
        this.failedEventPublications = failedEventPublications;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${interviewai.messaging.sqs.outbox-retry-interval}")
    public void resubmitFailedPublications() {
        ResubmissionOptions options = ResubmissionOptions.defaults().withBatchSize(batchSize);
        try {
            failedEventPublications.resubmit(options);
        } catch (RuntimeException exception) {
            log.warn("Failed to resubmit incomplete event publications", exception);
        }
    }
}
