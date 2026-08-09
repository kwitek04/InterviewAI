package com.interviewai.report.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportFailureMessagesTest {

    private static final String SECRET = "prompt-and-provider-output";

    @Test
    @DisplayName("invalid content is reported as a validation failure")
    void of_invalidContent_returnsValidationMessage() {
        assertThat(ReportFailureMessages.of(new InvalidReportContentException(SECRET)))
                .isEqualTo(ReportFailureMessages.INVALID_CONTENT);
    }

    @Test
    @DisplayName("invalid content wrapped by the retry bound is still reported as a validation failure")
    void of_wrappedInvalidContent_returnsValidationMessage() {
        ReportGenerationException wrapped = new ReportGenerationException(
                "stage failed", new InvalidReportContentException(SECRET));

        assertThat(ReportFailureMessages.of(wrapped)).isEqualTo(ReportFailureMessages.INVALID_CONTENT);
    }

    @Test
    @DisplayName("an exhausted retry bound is reported without provider detail")
    void of_generationFailure_returnsBoundedRetryMessage() {
        ReportGenerationException failure = new ReportGenerationException(
                "stage failed", new IllegalStateException(SECRET));

        assertThat(ReportFailureMessages.of(failure)).isEqualTo(ReportFailureMessages.GENERATION_FAILED);
    }

    @Test
    @DisplayName("unexpected failures never expose their own message")
    void of_unexpectedFailure_returnsGenericMessage() {
        assertThat(ReportFailureMessages.of(new IllegalStateException(SECRET)))
                .isEqualTo(ReportFailureMessages.UNEXPECTED_FAILURE);
    }

    @Test
    @DisplayName("a cyclic cause chain terminates instead of looping")
    void of_cyclicCauseChain_terminates() {
        IllegalStateException first = new IllegalStateException(SECRET);
        IllegalStateException second = new IllegalStateException(SECRET, first);
        first.initCause(second);

        assertThat(ReportFailureMessages.of(first)).isEqualTo(ReportFailureMessages.UNEXPECTED_FAILURE);
    }
}
