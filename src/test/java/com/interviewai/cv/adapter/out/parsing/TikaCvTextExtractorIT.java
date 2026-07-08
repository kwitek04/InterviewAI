package com.interviewai.cv.adapter.out.parsing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

class TikaCvTextExtractorIT {

    private final TikaCvTextExtractor extractor = new TikaCvTextExtractor();

    @Test
    @DisplayName("extracting text from a real PDF CV returns its known content")
    void extractText_fromRealPdf_containsKnownSubstrings() {
        byte[] pdfContent = readFixture("/fixtures/sample-cv.pdf");

        String text = extractor.extractText(pdfContent);

        assertThat(text).contains("Jane Doe");
        assertThat(text).contains("Senior Backend Engineer");
        assertThat(text).contains("Allegro");
    }

    private byte[] readFixture(String classpathLocation) {
        try (InputStream input = getClass().getResourceAsStream(classpathLocation)) {
            if (input == null) {
                throw new IllegalStateException("Fixture not found on classpath: " + classpathLocation);
            }
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
