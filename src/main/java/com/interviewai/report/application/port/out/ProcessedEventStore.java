package com.interviewai.report.application.port.out;

import com.interviewai.shared.SessionId;

import java.time.Duration;
import java.util.UUID;

/**
 * Durable claim store that makes interview-completed handling idempotent under
 * at-least-once SQS delivery.
 */
public interface ProcessedEventStore {

    /**
     * Attempts to acquire or reclaim the processing claim for {@code eventId}.
     */
    ClaimResult tryClaim(UUID eventId, SessionId sessionId, String ownerId, Duration lease);

    /**
     * Marks a claim owned by {@code ownerId} as completed.
     *
     * @throws IllegalStateException if the claim is missing or owned by someone else
     */
    void markCompleted(UUID eventId, String ownerId);

    boolean isCompleted(UUID eventId);

    sealed interface ClaimResult {

        record Acquired(int attemptCount) implements ClaimResult {
        }

        record AlreadyCompleted() implements ClaimResult {
        }

        /**
         * Another delivery still holds a non-stale claim.
         */
        record Busy() implements ClaimResult {
        }
    }
}
