package com.interviewai.interview.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_response_event")
class QuestionResponseEventEntity {

    @EmbeddedId
    private QuestionResponseEventId id;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuestionResponseEventEntity() {
    }

    private QuestionResponseEventEntity(
            UUID responseId,
            int sequence,
            String eventType,
            String payload,
            Instant createdAt) {
        this.id = QuestionResponseEventId.of(responseId, sequence);
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    static QuestionResponseEventEntity create(
            UUID responseId,
            int sequence,
            String eventType,
            String payload,
            Instant createdAt) {
        return new QuestionResponseEventEntity(responseId, sequence, eventType, payload, createdAt);
    }

    QuestionResponseEventId getId() {
        return id;
    }

    String getEventType() {
        return eventType;
    }

    String getPayload() {
        return payload;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
