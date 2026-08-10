package com.interviewai.report.application;

/**
 * Outcome of handling one interview-completed delivery.
 */
public enum InterviewCompletedProcessingOutcome {
    /**
     * Report is ready and the claim is completed — the SQS message may be deleted.
     */
    COMPLETED,
    /**
     * The event was already completed earlier — the SQS message may be deleted.
     */
    DUPLICATE,
    /**
     * Another delivery still holds a live claim — leave the message for later visibility.
     */
    BUSY,
    /**
     * Generation failed for this delivery — leave the message for SQS retry/DLQ.
     */
    FAILED
}
