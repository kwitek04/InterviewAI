package com.interviewai.report.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SynthesisResultTest {

    @Test
    @DisplayName("valid lists are trimmed and deduplicated")
    void validated_trimsAndDeduplicates() {
        SynthesisResult result = SynthesisResult.validated(
                List.of(" Clear ", "Clear", "Strong Java"),
                List.of("Needs depth"),
                List.of("Practice STAR", "Practice STAR "));

        assertThat(result.strengths()).containsExactly("Clear", "Strong Java");
        assertThat(result.weaknesses()).containsExactly("Needs depth");
        assertThat(result.recommendations()).containsExactly("Practice STAR");
    }

    @Test
    @DisplayName("empty strengths are rejected")
    void validated_withEmptyStrengths_throws() {
        assertThatThrownBy(() -> SynthesisResult.validated(List.of(), List.of("w"), List.of("r")))
                .isInstanceOf(InvalidReportContentException.class);
    }

    @Test
    @DisplayName("blank list entries are rejected")
    void validated_withBlankEntry_throws() {
        assertThatThrownBy(() -> SynthesisResult.validated(List.of("ok"), List.of("  "), List.of("r")))
                .isInstanceOf(InvalidReportContentException.class);
    }
}
