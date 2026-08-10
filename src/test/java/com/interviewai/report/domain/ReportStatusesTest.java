package com.interviewai.report.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportStatusesTest {

    @ParameterizedTest(name = "[{index}] {0} -> GENERATING")
    @MethodSource("allowedToGenerating")
    @DisplayName("PENDING, FAILED, and GENERATING may enter GENERATING")
    void toGenerating_fromAllowedStatuses_returnsGenerating(ReportStatus current) {
        assertThat(ReportStatuses.toGenerating(current)).isEqualTo(ReportStatus.GENERATING);
    }

    @Test
    @DisplayName("READY cannot transition to GENERATING")
    void toGenerating_fromReady_throws() {
        assertThatThrownBy(() -> ReportStatuses.toGenerating(ReportStatus.READY))
                .isInstanceOf(ReportTransitionException.class);
    }

    @Test
    @DisplayName("GENERATING may transition to READY")
    void toReady_fromGenerating_returnsReady() {
        assertThat(ReportStatuses.toReady(ReportStatus.GENERATING)).isEqualTo(ReportStatus.READY);
    }

    @ParameterizedTest(name = "[{index}] {0} cannot become READY")
    @MethodSource("forbiddenToReady")
    @DisplayName("only GENERATING may become READY")
    void toReady_fromForbiddenStatuses_throws(ReportStatus current) {
        assertThatThrownBy(() -> ReportStatuses.toReady(current))
                .isInstanceOf(ReportTransitionException.class);
    }

    @ParameterizedTest(name = "[{index}] {0} -> FAILED")
    @MethodSource("allowedToFailed")
    @DisplayName("PENDING and GENERATING may enter FAILED")
    void toFailed_fromAllowedStatuses_returnsFailed(ReportStatus current) {
        assertThat(ReportStatuses.toFailed(current)).isEqualTo(ReportStatus.FAILED);
    }

    @ParameterizedTest(name = "[{index}] {0} cannot become FAILED")
    @MethodSource("forbiddenToFailed")
    @DisplayName("READY and FAILED cannot transition to FAILED")
    void toFailed_fromForbiddenStatuses_throws(ReportStatus current) {
        assertThatThrownBy(() -> ReportStatuses.toFailed(current))
                .isInstanceOf(ReportTransitionException.class);
    }

    private static Stream<Arguments> allowedToGenerating() {
        return Stream.of(
                Arguments.of(ReportStatus.PENDING),
                Arguments.of(ReportStatus.FAILED),
                Arguments.of(ReportStatus.GENERATING));
    }

    private static Stream<Arguments> forbiddenToReady() {
        return Stream.of(
                Arguments.of(ReportStatus.PENDING),
                Arguments.of(ReportStatus.READY),
                Arguments.of(ReportStatus.FAILED));
    }

    private static Stream<Arguments> allowedToFailed() {
        return Stream.of(Arguments.of(ReportStatus.PENDING), Arguments.of(ReportStatus.GENERATING));
    }

    private static Stream<Arguments> forbiddenToFailed() {
        return Stream.of(Arguments.of(ReportStatus.READY), Arguments.of(ReportStatus.FAILED));
    }
}
