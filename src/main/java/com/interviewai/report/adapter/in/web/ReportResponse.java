package com.interviewai.report.adapter.in.web;

import com.interviewai.report.domain.InterviewReport;
import com.interviewai.report.domain.QuestionAssessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Fixed JSON contract for a ready interview report.
 */
record ReportResponse(
        UUID sessionId,
        String status,
        Instant generatedAt,
        List<AssessmentResponse> assessments,
        List<String> strengths,
        List<String> weaknesses,
        List<String> recommendations) {

    static ReportResponse from(InterviewReport report) {
        return new ReportResponse(
                report.sessionId().value(),
                report.status().name(),
                report.generatedAt(),
                report.assessments().stream().map(AssessmentResponse::from).toList(),
                report.strengths(),
                report.weaknesses(),
                report.recommendations());
    }

    record AssessmentResponse(
            int questionIndex,
            String question,
            String answer,
            int score,
            String scoreLabel,
            String rationale) {

        static AssessmentResponse from(QuestionAssessment assessment) {
            return new AssessmentResponse(
                    assessment.questionIndex(),
                    assessment.question(),
                    assessment.answer(),
                    assessment.score(),
                    scoreLabel(assessment.score()),
                    assessment.rationale());
        }

        private static String scoreLabel(int score) {
            return switch (score) {
                case 1 -> "Poor";
                case 2 -> "Weak";
                case 3 -> "Adequate";
                case 4 -> "Strong";
                case 5 -> "Excellent";
                default -> "Unknown";
            };
        }
    }
}
