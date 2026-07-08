package com.interviewai.cv.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private TextChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new TextChunker();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n", "\n\n\n"})
    @DisplayName("empty or blank text returns an empty list")
    void chunk_withEmptyOrBlankText_returnsEmptyList(String text) {
        assertThat(chunker.chunk(text)).isEmpty();
    }

    @Test
    @DisplayName("text shorter than 1500 characters produces exactly one trimmed chunk at index 0")
    void chunk_withShortText_returnsSingleTrimmedChunkAtIndexZero() {
        List<TextChunk> chunks = chunker.chunk("  Jane Doe\nSenior Backend Engineer  ");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().index()).isZero();
        assertThat(chunks.getFirst().text()).isEqualTo("Jane Doe\nSenior Backend Engineer");
    }

    @Test
    @DisplayName("two paragraphs that fit together are packed into one chunk")
    void chunk_withTwoFittingParagraphs_returnsSingleChunk() {
        List<TextChunk> chunks = chunker.chunk("First paragraph.\n\nSecond paragraph.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text()).isEqualTo("First paragraph.\n\nSecond paragraph.");
    }

    @Test
    @DisplayName("paragraphs exceeding the limit are split and the next chunk starts with the overlap sentence")
    void chunk_withParagraphsExceedingLimit_splitsWithOverlapSentenceAtStartOfNextChunk() {
        String firstParagraph = "X".repeat(900) + ". This is the overlap sentence.";
        String secondParagraph = "Y".repeat(600);
        String text = firstParagraph + "\n\n" + secondParagraph;

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo(firstParagraph);
        assertThat(chunks.get(1).text()).startsWith("This is the overlap sentence.");
        assertThat(chunks.get(1).text()).contains(secondParagraph);
    }

    @Test
    @DisplayName("a single paragraph longer than 1500 characters without blank lines is split on sentence boundaries")
    void chunk_withLongParagraphWithoutBlankLines_splitsOnSentenceBoundaries() {
        String firstSentence = "A".repeat(798) + ". ";
        String secondSentence = "B".repeat(798) + ". ";
        String closingSentence = "Closing sentence.";
        String text = firstSentence + secondSentence + closingSentence;

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.getFirst().text()).endsWith(".");
        assertThat(chunks.stream().map(TextChunk::text).anyMatch(chunk -> chunk.contains("Closing sentence.")))
                .isTrue();
    }

    @Test
    @DisplayName("a single sentence longer than 1500 characters is hard-split without looping")
    void chunk_withSingleSentenceLongerThanLimit_hardSplitsWithoutInfiniteLoop() {
        String text = "Z".repeat(2000);

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).hasSize(TextChunker.MAX_CHUNK_CHARS);
        assertThat(chunks.get(1).text()).hasSize(500);
        assertThat(chunks.get(0).text() + chunks.get(1).text()).isEqualTo(text);
    }

    @Test
    @DisplayName("Windows line endings are normalized before chunking")
    void chunk_withWindowsLineEndings_normalizesLineEndings() {
        List<TextChunk> chunks = chunker.chunk("First paragraph.\r\n\r\nSecond paragraph.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text()).isEqualTo("First paragraph.\n\nSecond paragraph.");
    }

    @Test
    @DisplayName("chunk indexes are consecutive and start at 0")
    void chunk_indexesAreConsecutiveStartingAtZero() {
        String text = "A".repeat(900) + ". Overlap sentence.\n\n" + "B".repeat(600);

        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks).extracting(TextChunk::index).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    @DisplayName("the same input always produces the same output")
    void chunk_isDeterministic() {
        String text = "Deterministic text. " + "word ".repeat(200) + "\n\nSecond paragraph.";

        List<TextChunk> firstRun = chunker.chunk(text);
        List<TextChunk> secondRun = chunker.chunk(text);

        assertThat(secondRun).isEqualTo(firstRun);
    }
}
