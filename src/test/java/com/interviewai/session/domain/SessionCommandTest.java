package com.interviewai.session.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SessionCommandTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    @DisplayName("AskQuestion rejects a null content at construction time")
    void askQuestion_withNullContent_throwsAtConstruction() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SessionCommand.AskQuestion(null, NOW));
    }

    @Test
    @DisplayName("AskQuestion rejects blank content at construction time")
    void askQuestion_withBlankContent_throwsAtConstruction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SessionCommand.AskQuestion("   ", NOW));
    }

    @Test
    @DisplayName("AskQuestion rejects a null timestamp at construction time")
    void askQuestion_withNullTimestamp_throwsAtConstruction() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SessionCommand.AskQuestion("Tell me about yourself", null));
    }

    @Test
    @DisplayName("SubmitAnswer rejects a null content at construction time")
    void submitAnswer_withNullContent_throwsAtConstruction() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SessionCommand.SubmitAnswer(null, NOW));
    }

    @Test
    @DisplayName("SubmitAnswer rejects blank content at construction time")
    void submitAnswer_withBlankContent_throwsAtConstruction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SessionCommand.SubmitAnswer("", NOW));
    }

    @Test
    @DisplayName("SubmitAnswer rejects a null timestamp at construction time")
    void submitAnswer_withNullTimestamp_throwsAtConstruction() {
        assertThatNullPointerException()
                .isThrownBy(() -> new SessionCommand.SubmitAnswer("I am a developer", null));
    }
}
