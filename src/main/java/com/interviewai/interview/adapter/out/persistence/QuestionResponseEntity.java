package com.interviewai.interview.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_response")
class QuestionResponseEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "accumulated_text", nullable = false)
    private String accumulatedText;

    @Column(name = "final_text")
    private String finalText;

    @Column(name = "last_event_sequence", nullable = false)
    private int lastEventSequence;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuestionResponseEntity() {
    }

    private QuestionResponseEntity(
            UUID id,
            UUID sessionId,
            String status,
            String accumulatedText,
            int lastEventSequence,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.status = status;
        this.accumulatedText = accumulatedText;
        this.lastEventSequence = lastEventSequence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static QuestionResponseEntity createPending(
            UUID id,
            UUID sessionId,
            Instant createdAt) {
        return new QuestionResponseEntity(
                id,
                sessionId,
                QuestionResponseStatusMapper.toStorage(com.interviewai.interview.domain.QuestionResponseStatus.PENDING),
                "",
                0,
                createdAt,
                createdAt);
    }

    UUID getId() {
        return id;
    }

    UUID getSessionId() {
        return sessionId;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getAccumulatedText() {
        return accumulatedText;
    }

    void setAccumulatedText(String accumulatedText) {
        this.accumulatedText = accumulatedText;
    }

    String getFinalText() {
        return finalText;
    }

    void setFinalText(String finalText) {
        this.finalText = finalText;
    }

    int getLastEventSequence() {
        return lastEventSequence;
    }

    void setLastEventSequence(int lastEventSequence) {
        this.lastEventSequence = lastEventSequence;
    }

    String getFailureMessage() {
        return failureMessage;
    }

    void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
