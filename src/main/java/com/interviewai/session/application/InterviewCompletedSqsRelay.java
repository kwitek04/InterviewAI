package com.interviewai.session.application;

import com.interviewai.session.application.port.out.CompletedInterviewPublisher;
import com.interviewai.shared.InterviewCompletedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Relays {@link InterviewCompletedEvent} publications from the transactional outbox
 * to external messaging infrastructure.
 */
@Component
public class InterviewCompletedSqsRelay {

    private final CompletedInterviewPublisher completedInterviewPublisher;

    public InterviewCompletedSqsRelay(CompletedInterviewPublisher completedInterviewPublisher) {
        this.completedInterviewPublisher = completedInterviewPublisher;
    }

    @ApplicationModuleListener
    void onInterviewCompleted(InterviewCompletedEvent event) {
        completedInterviewPublisher.publish(event);
    }
}
