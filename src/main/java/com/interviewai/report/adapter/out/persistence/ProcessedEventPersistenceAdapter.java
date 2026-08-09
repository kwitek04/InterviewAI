package com.interviewai.report.adapter.out.persistence;

import com.interviewai.report.application.port.out.ProcessedEventStore;
import com.interviewai.report.domain.ProcessedEventStatus;
import com.interviewai.shared.SessionId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
class ProcessedEventPersistenceAdapter implements ProcessedEventStore {

    private final ProcessedEventJpaRepository repository;
    private final Clock clock;

    ProcessedEventPersistenceAdapter(ProcessedEventJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ClaimResult tryClaim(UUID eventId, SessionId sessionId, String ownerId, Duration lease) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(lease, "lease must not be null");
        if (ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (lease.isNegative() || lease.isZero()) {
            throw new IllegalArgumentException("lease must be positive");
        }

        Instant now = clock.instant();
        ProcessedEventEntity existing = repository.findById(eventId).orElse(null);
        if (existing == null) {
            try {
                repository.saveAndFlush(ProcessedEventEntity.createProcessing(
                        eventId, sessionId.value(), ownerId, now));
                return new ClaimResult.Acquired(1);
            } catch (DataIntegrityViolationException exception) {
                existing = repository.findById(eventId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Claim insert raced but row is missing for event " + eventId, exception));
            }
        }

        if (ProcessedEventStatus.COMPLETED.name().equals(existing.getStatus())) {
            return new ClaimResult.AlreadyCompleted();
        }

        Instant claimedAt = existing.getClaimedAt();
        boolean leaseExpired = claimedAt == null || !claimedAt.plus(lease).isAfter(now);
        if (!leaseExpired) {
            return new ClaimResult.Busy();
        }

        existing.setStatus(ProcessedEventStatus.PROCESSING.name());
        existing.setClaimOwner(ownerId);
        existing.setClaimedAt(now);
        existing.setAttemptCount(existing.getAttemptCount() + 1);
        existing.setCompletedAt(null);
        repository.save(existing);
        return new ClaimResult.Acquired(existing.getAttemptCount());
    }

    @Override
    @Transactional
    public void markCompleted(UUID eventId, String ownerId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");

        ProcessedEventEntity entity = repository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException("No processing claim for event " + eventId));
        if (ProcessedEventStatus.COMPLETED.name().equals(entity.getStatus())) {
            return;
        }
        if (!ownerId.equals(entity.getClaimOwner())) {
            throw new IllegalStateException(
                    "Cannot complete event " + eventId + ": claim owned by " + entity.getClaimOwner());
        }
        entity.setStatus(ProcessedEventStatus.COMPLETED.name());
        entity.setCompletedAt(clock.instant());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompleted(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return repository.findById(eventId)
                .map(entity -> ProcessedEventStatus.COMPLETED.name().equals(entity.getStatus()))
                .orElse(false);
    }
}
