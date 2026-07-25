package com.interviewai.report.adapter.in.messaging;

/**
 * Marker for worker-profile SQS consumer infrastructure.
 * <p>
 * Polling and acknowledgement are implemented by a later ticket; this bean exists so
 * the worker runtime can be distinguished from the API runtime.
 */
public class InterviewCompletedQueueConsumer {

    public boolean isEnabled() {
        return true;
    }
}
