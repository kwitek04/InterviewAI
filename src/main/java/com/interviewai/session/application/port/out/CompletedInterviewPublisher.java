package com.interviewai.session.application.port.out;

import com.interviewai.shared.InterviewCompletedEvent;

/**
 * Publishes a completed-interview fact to external messaging infrastructure.
 */
public interface CompletedInterviewPublisher {

    /**
     * Sends the given completion event. Implementations must throw when the broker
     * does not confirm acceptance so that the transactional outbox can retry.
     */
    void publish(InterviewCompletedEvent event);
}
