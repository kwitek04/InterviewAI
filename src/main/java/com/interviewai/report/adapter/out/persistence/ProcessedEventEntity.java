package com.interviewai.report.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_event")
class ProcessedEventEntity {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "claim_owner", length = 128)
    private String claimOwner;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ProcessedEventEntity() {
    }

    static ProcessedEventEntity createProcessing(
            UUID eventId, UUID sessionId, String claimOwner, Instant claimedAt) {
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.eventId = eventId;
        entity.sessionId = sessionId;
        entity.status = "PROCESSING";
        entity.claimOwner = claimOwner;
        entity.claimedAt = claimedAt;
        entity.attemptCount = 1;
        return entity;
    }

    UUID getEventId() {
        return eventId;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getClaimOwner() {
        return claimOwner;
    }

    void setClaimOwner(String claimOwner) {
        this.claimOwner = claimOwner;
    }

    Instant getClaimedAt() {
        return claimedAt;
    }

    void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    int getAttemptCount() {
        return attemptCount;
    }

    void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    Instant getCompletedAt() {
        return completedAt;
    }

    void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
