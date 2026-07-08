package com.interviewai.cv.adapter.out.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgVectorLiteralTest {

    @Test
    @DisplayName("pgvector literals are formatted without scientific notation")
    void toPgVectorLiteral_formatsValuesWithoutScientificNotation() {
        float[] embedding = new float[]{1.25f, -0.5f, 0.000001f};

        String literal = PgVectorCvChunkStore.toPgVectorLiteral(embedding);

        assertThat(literal).isEqualTo("[1.25,-0.5,0.000001]");
        assertThat(literal).doesNotContain("E");
    }
}
