package com.interviewai.cv.application;

import com.interviewai.cv.application.port.out.CvDocumentRepository;
import com.interviewai.cv.application.port.out.CvTextExtractor;
import com.interviewai.cv.application.port.out.FileStorage;
import com.interviewai.cv.application.port.out.StoredFile;
import com.interviewai.cv.domain.CvDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final byte[] VALID_PDF = "%PDF-1.4 fake cv content".getBytes(StandardCharsets.UTF_8);

    @Mock
    private FileStorage fileStorage;

    @Mock
    private CvTextExtractor cvTextExtractor;

    @Mock
    private CvDocumentRepository cvDocumentRepository;

    private CvApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CvApplicationService(
                fileStorage, cvTextExtractor, cvDocumentRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("uploading a valid PDF stores it, extracts its text, and persists the document")
    void uploadCv_withValidPdf_storesExtractsAndPersists() {
        when(fileStorage.store(anyString(), eq(VALID_PDF), eq("application/pdf")))
                .thenReturn(new StoredFile("ignored", VALID_PDF.length));
        when(cvTextExtractor.extractText(VALID_PDF)).thenReturn("Jane Doe, Senior Backend Engineer");

        CvDocument document = service.uploadCv("cv.pdf", VALID_PDF, "We are hiring a backend engineer.");

        assertThat(document.fileName()).isEqualTo("cv.pdf");
        assertThat(document.extractedText()).isEqualTo("Jane Doe, Senior Backend Engineer");
        assertThat(document.jobOffer()).isEqualTo("We are hiring a backend engineer.");
        assertThat(document.uploadedAt()).isEqualTo(NOW);
        verify(cvDocumentRepository).save(document);
    }

    @Test
    @DisplayName("uploading a valid PDF stores it under key cv/{cvId}.pdf")
    void uploadCv_storesUnderExpectedKeyFormat() {
        when(fileStorage.store(anyString(), eq(VALID_PDF), eq("application/pdf")))
                .thenReturn(new StoredFile("ignored", VALID_PDF.length));
        when(cvTextExtractor.extractText(VALID_PDF)).thenReturn("Jane Doe");

        CvDocument document = service.uploadCv("cv.pdf", VALID_PDF, "job offer");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(keyCaptor.capture(), eq(VALID_PDF), eq("application/pdf"));
        assertThat(keyCaptor.getValue()).isEqualTo("cv/" + document.id().value() + ".pdf");
        assertThat(document.storageKey()).isEqualTo(keyCaptor.getValue());
    }

    static Stream<Arguments> invalidUploads() {
        return Stream.of(
                Arguments.of("blank file name", "   ", VALID_PDF, "job offer"),
                Arguments.of("null file name", null, VALID_PDF, "job offer"),
                Arguments.of("empty file", "cv.pdf", new byte[0], "job offer"),
                Arguments.of(
                        "oversized file", "cv.pdf", new byte[(int) (5L * 1024 * 1024) + 1], "job offer"),
                Arguments.of("non-pdf content", "cv.pdf", "not a pdf".getBytes(StandardCharsets.UTF_8), "job offer"),
                Arguments.of("blank job offer", "cv.pdf", VALID_PDF, "   "),
                Arguments.of("null job offer", "cv.pdf", VALID_PDF, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUploads")
    @DisplayName("uploading an invalid CV throws InvalidCvUploadException without storing or persisting anything")
    void uploadCv_withInvalidInput_throwsInvalidCvUploadException(
            String description, String fileName, byte[] content, String jobOffer) {
        assertThatThrownBy(() -> service.uploadCv(fileName, content, jobOffer))
                .isInstanceOf(InvalidCvUploadException.class);

        verifyNoInteractions(fileStorage, cvTextExtractor, cvDocumentRepository);
    }

    @Test
    @DisplayName("uploading a CV whose text cannot be extracted throws InvalidCvUploadException and does not persist")
    void uploadCv_whenExtractedTextIsBlank_throwsInvalidCvUploadExceptionAndDoesNotPersist() {
        when(fileStorage.store(anyString(), any(byte[].class), anyString()))
                .thenReturn(new StoredFile("ignored", VALID_PDF.length));
        when(cvTextExtractor.extractText(VALID_PDF)).thenReturn("   ");

        assertThatThrownBy(() -> service.uploadCv("cv.pdf", VALID_PDF, "job offer"))
                .isInstanceOf(InvalidCvUploadException.class);

        verify(cvDocumentRepository, never()).save(any());
    }
}
