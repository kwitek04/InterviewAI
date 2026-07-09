package com.interviewai.cv.application;

import com.interviewai.cv.application.port.out.CvChunkStore;
import com.interviewai.cv.application.port.out.CvDocumentRepository;
import com.interviewai.cv.application.port.out.CvTextExtractor;
import com.interviewai.cv.application.port.out.EmbeddedChunk;
import com.interviewai.cv.application.port.out.EmbeddingGenerator;
import com.interviewai.cv.application.port.out.FileStorage;
import com.interviewai.cv.application.port.out.StoredFile;
import com.interviewai.cv.domain.CvDocument;
import com.interviewai.cv.domain.TextChunk;
import com.interviewai.cv.domain.TextChunker;
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
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final byte[] VALID_PDF = "%PDF-1.4 fake cv content".getBytes(StandardCharsets.UTF_8);
    private static final String EXTRACTED_TEXT = "Jane Doe, Senior Backend Engineer";

    @Mock
    private FileStorage fileStorage;

    @Mock
    private CvTextExtractor cvTextExtractor;

    @Mock
    private CvDocumentRepository cvDocumentRepository;

    @Mock
    private TextChunker textChunker;

    @Mock
    private EmbeddingGenerator embeddingGenerator;

    @Mock
    private CvChunkStore cvChunkStore;

    private CvApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CvApplicationService(
                fileStorage,
                cvTextExtractor,
                cvDocumentRepository,
                textChunker,
                embeddingGenerator,
                cvChunkStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("uploading a valid PDF stores it, extracts its text, persists the document, and embeds its chunks")
    void uploadCv_withValidPdf_storesExtractsPersistsAndEmbedsChunks() {
        when(fileStorage.store(anyString(), eq(VALID_PDF), eq("application/pdf")))
                .thenReturn(new StoredFile("ignored", VALID_PDF.length));
        when(cvTextExtractor.extractText(VALID_PDF)).thenReturn(EXTRACTED_TEXT);
        when(textChunker.chunk(EXTRACTED_TEXT)).thenReturn(List.of(
                new TextChunk(0, "Jane Doe, Senior Backend Engineer")));
        when(embeddingGenerator.embedAll(List.of("Jane Doe, Senior Backend Engineer")))
                .thenReturn(List.of(new float[]{0.1f, 0.2f}));

        CvUploadResult result = service.uploadCv("cv.pdf", VALID_PDF, "We are hiring a backend engineer.");

        assertThat(result.chunkCount()).isEqualTo(1);
        assertThat(result.document().fileName()).isEqualTo("cv.pdf");
        assertThat(result.document().extractedText()).isEqualTo(EXTRACTED_TEXT);
        assertThat(result.document().jobOffer()).isEqualTo("We are hiring a backend engineer.");
        assertThat(result.document().uploadedAt()).isEqualTo(NOW);
        verify(cvDocumentRepository).save(result.document());

        ArgumentCaptor<List<EmbeddedChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(cvChunkStore).saveAll(eq(result.document().id()), chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(1);
        EmbeddedChunk savedChunk = chunksCaptor.getValue().getFirst();
        assertThat(savedChunk.index()).isZero();
        assertThat(savedChunk.content()).isEqualTo("Jane Doe, Senior Backend Engineer");
        assertThat(savedChunk.embedding()).containsExactly(0.1f, 0.2f);
    }

    @Test
    @DisplayName("uploading plain text skips PDF validation and extraction")
    void uploadCv_withPlainText_skipsPdfValidationAndExtraction() {
        String plainText = "Jane Doe led a Kafka migration at Allegro.";
        when(fileStorage.store(anyString(), any(byte[].class), eq("text/plain")))
                .thenReturn(new StoredFile("ignored", plainText.length()));
        when(textChunker.chunk(plainText)).thenReturn(List.of(new TextChunk(0, plainText)));
        when(embeddingGenerator.embedAll(List.of(plainText))).thenReturn(List.of(new float[]{0.3f}));

        CvUploadResult result = service.uploadCv(
                CvUploadCommand.fromPlainText("cv.txt", plainText, "Backend role with Kafka"));

        assertThat(result.document().extractedText()).isEqualTo(plainText);
        assertThat(result.chunkCount()).isEqualTo(1);
        verify(cvTextExtractor, never()).extractText(any());
        verify(cvChunkStore).saveAll(eq(result.document().id()), any());
    }

    @Test
    void uploadCv_storesUnderExpectedKeyFormat() {
        when(fileStorage.store(anyString(), eq(VALID_PDF), eq("application/pdf")))
                .thenReturn(new StoredFile("ignored", VALID_PDF.length));
        when(cvTextExtractor.extractText(VALID_PDF)).thenReturn("Jane Doe");
        when(textChunker.chunk("Jane Doe")).thenReturn(List.of(new TextChunk(0, "Jane Doe")));
        when(embeddingGenerator.embedAll(List.of("Jane Doe"))).thenReturn(List.of(new float[]{0.5f}));

        CvUploadResult result = service.uploadCv("cv.pdf", VALID_PDF, "job offer");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage).store(keyCaptor.capture(), eq(VALID_PDF), eq("application/pdf"));
        assertThat(keyCaptor.getValue()).isEqualTo("cv/" + result.document().id().value() + ".pdf");
        assertThat(result.document().storageKey()).isEqualTo(keyCaptor.getValue());
    }

    @Test
    @DisplayName("an embedding failure during upload propagates without persisting chunks")
    void uploadCv_whenEmbeddingFails_propagatesExceptionWithoutSavingChunks() {
        when(fileStorage.store(anyString(), eq(VALID_PDF), eq("application/pdf")))
                .thenReturn(new StoredFile("ignored", VALID_PDF.length));
        when(cvTextExtractor.extractText(VALID_PDF)).thenReturn(EXTRACTED_TEXT);
        when(textChunker.chunk(EXTRACTED_TEXT)).thenReturn(List.of(new TextChunk(0, EXTRACTED_TEXT)));
        doThrow(new RuntimeException("embedding failed")).when(embeddingGenerator).embedAll(anyList());

        assertThatThrownBy(() -> service.uploadCv("cv.pdf", VALID_PDF, "job offer"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("embedding failed");

        verify(cvDocumentRepository).save(any(CvDocument.class));
        verify(cvChunkStore, never()).saveAll(any(), any());
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

        verifyNoInteractions(fileStorage, cvTextExtractor, cvDocumentRepository, textChunker, embeddingGenerator, cvChunkStore);
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
        verifyNoInteractions(textChunker, embeddingGenerator, cvChunkStore);
    }
}
