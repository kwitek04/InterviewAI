package com.interviewai.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingMetricTest {

    @Test
    @DisplayName("matches facts case-insensitively")
    void isGrounded_matchesCaseInsensitively() {
        assertThat(GroundingMetric.isGrounded("Tell me about your Kafka experience.", List.of("Kafka"))).isTrue();
        assertThat(GroundingMetric.isGrounded("Tell me about your kafka experience.", List.of("Kafka"))).isTrue();
        assertThat(GroundingMetric.findMatchedFact("Worked at Allegro?", List.of("PostgreSQL", "Allegro")))
                .isEqualTo("Allegro");
    }

    @Test
    @DisplayName("returns false when no fact appears in the question")
    void isGrounded_whenNoFactMatches_returnsFalse() {
        assertThat(GroundingMetric.isGrounded("What motivates you?", List.of("Spring Boot", "AWS"))).isFalse();
        assertThat(GroundingMetric.findMatchedFact("What motivates you?", List.of("Spring Boot"))).isNull();
    }
}
