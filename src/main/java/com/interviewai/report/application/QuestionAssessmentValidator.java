package com.interviewai.report.application;

import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

        List<CompletedInterviewSnapshot.AnsweredQuestion> pairs = snapshot.answeredQuestions();
        int expectedCount = pairs.size();
        if (expectedCount == 0) {
            throw new InvalidReportContentException("Completed interview has no answered questions");
        }
        if (scoredAnswers.size() != expectedCount) {
            throw new InvalidReportContentException(
                    "Expected " + expectedCount + " assessments but received " + scoredAnswers.size());
        }

        List<ScoredAnswer> ordered = orderScores(pairs, scoredAnswers);
        List<QuestionAssessment> assessments = new ArrayList<>(expectedCount);
        for (int i = 0; i < expectedCount; i++) {
            ScoredAnswer scored = Objects.requireNonNull(ordered.get(i), "scored answer must not be null");
            CompletedInterviewSnapshot.AnsweredQuestion pair = pairs.get(i);
            assessments.add(new QuestionAssessment(
                    pair.questionIndex(),
                    pair.question(),
                    pair.answer(),
                    scored.score(),
                    scored.rationale().trim()));
        }
        return List.copyOf(assessments);
    }

    /**
     * Prefers matching by {@code questionIndex} when the model returns a permutation of the
     * transcript indices; otherwise binds by list order (local models often repeat index 0).
     */
    private static List<ScoredAnswer> orderScores(
            List<CompletedInterviewSnapshot.AnsweredQuestion> pairs, List<ScoredAnswer> scoredAnswers) {
        Set<Integer> expectedIndices = new HashSet<>();
        for (CompletedInterviewSnapshot.AnsweredQuestion pair : pairs) {
            expectedIndices.add(pair.questionIndex());
        }

        Map<Integer, ScoredAnswer> byIndex = new HashMap<>();
        boolean uniqueAndComplete = true;
        for (ScoredAnswer scored : scoredAnswers) {
            Objects.requireNonNull(scored, "scored answer must not be null");
            if (!expectedIndices.contains(scored.questionIndex()) || byIndex.put(scored.questionIndex(), scored) != null) {
                uniqueAndComplete = false;
                break;
            }
        }
        uniqueAndComplete = uniqueAndComplete && byIndex.size() == expectedIndices.size();

        if (uniqueAndComplete) {
            List<ScoredAnswer> ordered = new ArrayList<>(pairs.size());
            for (CompletedInterviewSnapshot.AnsweredQuestion pair : pairs) {
                ordered.add(byIndex.get(pair.questionIndex()));
            }
            return ordered;
        }

        return List.copyOf(scoredAnswers);
    }
}
