package com.interviewai.report.domain;

import com.interviewai.shared.SessionId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable interview feedback report aggregate.
 */
public record InterviewReport(
        UUID id,
        SessionId sessionId,
        ReportStatus status,
        List<QuestionAssessment> assessments,
        List<String> strengths,
        List<String> weaknesses,
        List<String> recommendations,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant generatedAt,
        long version) {

    public InterviewReport {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        assessments = List.copyOf(Objects.requireNonNullElse(assessments, List.of()));
        strengths = List.copyOf(Objects.requireNonNullElse(strengths, List.of()));
        weaknesses = List.copyOf(Objects.requireNonNullElse(weaknesses, List.of()));
        recommendations = List.copyOf(Objects.requireNonNullElse(recommendations, List.of()));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (status == ReportStatus.READY) {
            if (assessments.isEmpty() || strengths.isEmpty() || weaknesses.isEmpty() || recommendations.isEmpty()) {
                throw new IllegalArgumentException("READY reports require assessments and synthesis lists");
            }
            Objects.requireNonNull(generatedAt, "generatedAt must not be null for READY reports");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0");
        }
    }

    public static InterviewReport pending(UUID id, SessionId sessionId, Instant now) {
        return new InterviewReport(
                id,
                sessionId,
                ReportStatus.PENDING,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                now,
                now,
                null,
                0L);
    }

    public InterviewReport markGenerating(Instant now) {
        return new InterviewReport(
                id,
                sessionId,
                ReportStatuses.toGenerating(status),
                assessments,
                strengths,
                weaknesses,
                recommendations,
                null,
                createdAt,
                now,
                generatedAt,
                version);
    }

    public InterviewReport markReady(
            List<QuestionAssessment> readyAssessments,
            List<String> readyStrengths,
            List<String> readyWeaknesses,
            List<String> readyRecommendations,
            Instant now) {
        return new InterviewReport(
                id,
                sessionId,
                ReportStatuses.toReady(status),
                readyAssessments,
                readyStrengths,
                readyWeaknesses,
                readyRecommendations,
                null,
                createdAt,
                now,
                now,
                version);
    }

    public InterviewReport markFailed(String message, Instant now) {
        Objects.requireNonNull(message, "message must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return new InterviewReport(
                id,
                sessionId,
                ReportStatuses.toFailed(status),
                assessments,
                strengths,
                weaknesses,
                recommendations,
                message,
                createdAt,
                now,
                generatedAt,
                version);
    }

    public boolean isReady() {
        return status == ReportStatus.READY;
    }
}
