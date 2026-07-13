package com.interviewai.interview.adapter.in.web;

import com.interviewai.interview.application.InvalidLastEventIdException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LastEventIdParserTest {

    @Test
    @DisplayName("missing Last-Event-ID replays from the beginning")
    void parse_missingHeader_returnsZero() {
        assertThat(LastEventIdParser.parse(null)).isZero();
        assertThat(LastEventIdParser.parse("   ")).isZero();
    }

    @Test
    @DisplayName("positive Last-Event-ID is accepted")
    void parse_positiveInteger_returnsValue() {
        assertThat(LastEventIdParser.parse("17")).isEqualTo(17);
        assertThat(LastEventIdParser.parse(" 2 ")).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", "1.5"})
    @DisplayName("malformed or negative Last-Event-ID is rejected")
    void parse_invalidValues_throw(String header) {
        assertThatThrownBy(() -> LastEventIdParser.parse(header))
                .isInstanceOf(InvalidLastEventIdException.class);
    }
}
