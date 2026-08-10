package com.interviewai.report.domain;

import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterviewReportTest {

    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private static final SessionId SESSION_ID = SessionId.generate();

    @Test
    @DisplayName("a READY report cannot be marked generating again")
    void markGenerating_whenReady_throws() {
        InterviewReport ready = readyReport();

        assertThatThrownBy(() -> ready.markGenerating(NOW.plusSeconds(1)))
                .isInstanceOf(ReportTransitionException.class);
        assertThat(ready.status()).isEqualTo(ReportStatus.READY);
    }

    @Test
    @DisplayName("a READY report cannot be marked failed")
    void markFailed_whenReady_throws() {
        InterviewReport ready = readyReport();

        assertThatThrownBy(() -> ready.markFailed("should not happen", NOW.plusSeconds(1)))
                .isInstanceOf(ReportTransitionException.class);
    }

    @Test
    @DisplayName("PENDING can move to GENERATING and then READY with payload")
    void pending_toGenerating_toReady_succeeds() {
        InterviewReport pending = InterviewReport.pending(UUID.randomUUID(), SESSION_ID, NOW);

        InterviewReport ready = pending.markGenerating(NOW.plusSeconds(1)).markReady(
                List.of(assessment()),
                List.of("Clear communication"),
                List.of("Needs deeper examples"),
                List.of("Prepare STAR stories"),
                NOW.plusSeconds(2));

        assertThat(ready.status()).isEqualTo(ReportStatus.READY);
        assertThat(ready.assessments()).hasSize(1);
        assertThat(ready.generatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(ready.failureMessage()).isNull();
    }

    private static InterviewReport readyReport() {
        return InterviewReport.pending(UUID.randomUUID(), SESSION_ID, NOW)
                .markGenerating(NOW.plusSeconds(1))
                .markReady(
                        List.of(assessment()),
                        List.of("Clear communication"),
                        List.of("Needs deeper examples"),
                        List.of("Prepare STAR stories"),
                        NOW.plusSeconds(2));
    }

    private static QuestionAssessment assessment() {
        return new QuestionAssessment(0, "Tell me about yourself", "I am a backend developer", 4, "Solid overview");
    }
}
