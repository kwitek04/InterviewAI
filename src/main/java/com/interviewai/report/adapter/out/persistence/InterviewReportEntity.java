package com.interviewai.report.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interview_report")
class InterviewReportEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false, unique = true)
    private UUID sessionId;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected InterviewReportEntity() {
    }

    static InterviewReportEntity create(UUID id, UUID sessionId, String status, Instant now) {
        InterviewReportEntity entity = new InterviewReportEntity();
        entity.id = id;
        entity.sessionId = sessionId;
        entity.status = status;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.version = 0L;
        return entity;
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

    String getPayload() {
        return payload;
    }

    void setPayload(String payload) {
        this.payload = payload;
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

    Instant getGeneratedAt() {
        return generatedAt;
    }

    void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    long getVersion() {
        return version;
    }
}
