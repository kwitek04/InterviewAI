package com.interviewai.report.application;

import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Validates stage-1 scored answers and binds them to transcript question/answer text.
 */
public final class QuestionAssessmentValidator {

    private QuestionAssessmentValidator() {
    }

    /**
     * Raw scored answer produced by stage 1 before transcript text is bound.
     */
    public record ScoredAnswer(int questionIndex, int score, String rationale) {

        public ScoredAnswer {
            if (questionIndex < 0) {
                throw new InvalidReportContentException("questionIndex must be >= 0");
            }
            if (score < 1 || score > 5) {
                throw new InvalidReportContentException("score must be between 1 and 5");
            }
            Objects.requireNonNull(rationale, "rationale must not be null");
            if (rationale.isBlank()) {
                throw new InvalidReportContentException("rationale must not be blank");
            }
        }
    }

    public static List<QuestionAssessment> validateAndBind(
            CompletedInterviewSnapshot snapshot, List<ScoredAnswer> scoredAnswers) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(scoredAnswers, "scoredAnswers must not be null");

        int expectedCount = snapshot.answeredQuestions().size();
        if (expectedCount == 0) {
            throw new InvalidReportContentException("Completed interview has no answered questions");
        }
        if (scoredAnswers.size() != expectedCount) {
            throw new InvalidReportContentException(
                    "Expected " + expectedCount + " assessments but received " + scoredAnswers.size());
        }

        List<QuestionAssessment> assessments = new ArrayList<>(expectedCount);
        for (int i = 0; i < scoredAnswers.size(); i++) {
            ScoredAnswer scored = Objects.requireNonNull(scoredAnswers.get(i), "scored answer must not be null");
            if (scored.questionIndex() != i) {
                throw new InvalidReportContentException(
                        "Assessments must be ordered by questionIndex; expected " + i
                                + " but was " + scored.questionIndex());
            }
            CompletedInterviewSnapshot.AnsweredQuestion pair = snapshot.answeredQuestions().get(i);
            assessments.add(new QuestionAssessment(
                    pair.questionIndex(),
                    pair.question(),
                    pair.answer(),
                    scored.score(),
                    scored.rationale().trim()));
        }
        return List.copyOf(assessments);
    }
}
