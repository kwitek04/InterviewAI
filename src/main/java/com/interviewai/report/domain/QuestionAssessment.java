package com.interviewai.report.domain;

import java.util.Objects;

/**
 * Evaluation of one answered interview question.
 */
public record QuestionAssessment(
        int questionIndex,
        String question,
        String answer,
        int score,
        String rationale) {

    public QuestionAssessment {
        if (questionIndex < 0) {
            throw new IllegalArgumentException("questionIndex must be >= 0");
        }
        Objects.requireNonNull(question, "question must not be null");
        if (question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        Objects.requireNonNull(answer, "answer must not be null");
        if (answer.isBlank()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("score must be between 1 and 5");
        }
        Objects.requireNonNull(rationale, "rationale must not be null");
        if (rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must not be blank");
        }
    }
}
