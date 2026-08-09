package com.interviewai.report.application;

import com.interviewai.report.application.QuestionAssessmentValidator.ScoredAnswer;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionAssessmentValidatorTest {

    private static final SessionId SESSION_ID = SessionId.generate();

    private static final CompletedInterviewSnapshot SNAPSHOT = new CompletedInterviewSnapshot(
            SESSION_ID,
            List.of(
                    new CompletedInterviewSnapshot.AnsweredQuestion(0, "Q0", "A0"),
                    new CompletedInterviewSnapshot.AnsweredQuestion(1, "Q1", "A1")));

    @Test
    @DisplayName("valid ordered scores bind transcript question and answer text")
    void validateAndBind_withOrderedScores_bindsTranscriptText() {
        List<QuestionAssessment> assessments = QuestionAssessmentValidator.validateAndBind(
                SNAPSHOT,
                List.of(
                        new ScoredAnswer(0, 4, "Good"),
                        new ScoredAnswer(1, 3, "Adequate")));

        assertThat(assessments).containsExactly(
                new QuestionAssessment(0, "Q0", "A0", 4, "Good"),
                new QuestionAssessment(1, "Q1", "A1", 3, "Adequate"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidScores")
    @DisplayName("invalid stage-1 payloads are rejected")
    void validateAndBind_withInvalidScores_throws(String caseName, List<ScoredAnswer> scores) {
        assertThatThrownBy(() -> QuestionAssessmentValidator.validateAndBind(SNAPSHOT, scores))
                .isInstanceOf(InvalidReportContentException.class);
    }

    @Test
    @DisplayName("score outside 1..5 is rejected at ScoredAnswer construction")
    void scoredAnswer_withOutOfRangeScore_throws() {
        assertThatThrownBy(() -> new ScoredAnswer(0, 6, "too high"))
                .isInstanceOf(InvalidReportContentException.class);
        assertThatThrownBy(() -> new ScoredAnswer(0, 0, "too low"))
                .isInstanceOf(InvalidReportContentException.class);
    }

    @Test
    @DisplayName("blank rationale is rejected")
    void scoredAnswer_withBlankRationale_throws() {
        assertThatThrownBy(() -> new ScoredAnswer(0, 3, "  "))
                .isInstanceOf(InvalidReportContentException.class);
    }

    private static Stream<Arguments> invalidScores() {
        return Stream.of(
                Arguments.of("missing assessment", List.of(new ScoredAnswer(0, 4, "Good"))),
                Arguments.of(
                        "extra assessment",
                        List.of(
                                new ScoredAnswer(0, 4, "Good"),
                                new ScoredAnswer(1, 3, "Ok"),
                                new ScoredAnswer(2, 2, "Extra"))),
                Arguments.of(
                        "out of order",
                        List.of(
                                new ScoredAnswer(1, 3, "Ok"),
                                new ScoredAnswer(0, 4, "Good"))),
                Arguments.of(
                        "duplicate index",
                        List.of(
                                new ScoredAnswer(0, 4, "Good"),
                                new ScoredAnswer(0, 3, "Duplicate"))),
                Arguments.of(
                        "index outside the transcript",
                        List.of(
                                new ScoredAnswer(0, 4, "Good"),
                                new ScoredAnswer(5, 3, "Ok"))));
    }
}
