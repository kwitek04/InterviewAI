package com.interviewai.cv.application;

import com.interviewai.cv.application.port.out.CvChunkStore;
import com.interviewai.cv.application.port.out.CvDocumentRepository;
import com.interviewai.cv.application.port.out.EmbeddingGenerator;
import com.interviewai.cv.application.port.out.ScoredChunk;
import com.interviewai.cv.domain.CvDocument;
import com.interviewai.shared.CvId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvRetrievalServiceTest {

    private static final Instant UPLOADED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private CvDocumentRepository cvDocumentRepository;

    @Mock
    private EmbeddingGenerator embeddingGenerator;

    @Mock
    private CvChunkStore cvChunkStore;

    private CvRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new CvRetrievalService(cvDocumentRepository, embeddingGenerator, cvChunkStore);
    }

    @Test
    @DisplayName("retrieveContext returns the job offer and relevant chunk texts in ranked order")
    void retrieveContext_withKnownCv_returnsJobOfferAndRelevantChunksInOrder() {
        CvId cvId = CvId.generate();
        CvDocument document = new CvDocument(
                cvId,
                "cv.pdf",
                "cv/" + cvId.value() + ".pdf",
                "Jane Doe, Senior Backend Engineer",
                "Backend engineer role",
                UPLOADED_AT);
        float[] queryEmbedding = new float[]{0.1f, 0.2f};

        when(cvDocumentRepository.findById(cvId)).thenReturn(Optional.of(document));
        when(embeddingGenerator.embed("Spring Boot experience")).thenReturn(queryEmbedding);
        when(cvChunkStore.findMostSimilar(cvId, queryEmbedding, 2)).thenReturn(List.of(
                new ScoredChunk("Led Spring Boot migration", 0.95),
                new ScoredChunk("Built REST APIs with Java", 0.88)));

        CvRetrievalService.CvContext context = service.retrieveContext(cvId, "Spring Boot experience", 2);

        assertThat(context.jobOffer()).isEqualTo("Backend engineer role");
        assertThat(context.relevantChunks())
                .containsExactly("Led Spring Boot migration", "Built REST APIs with Java");
        verify(cvChunkStore).findMostSimilar(eq(cvId), eq(queryEmbedding), eq(2));
    }

    @Test
    @DisplayName("retrieveContext for an unknown CV throws CvNotFoundException")
    void retrieveContext_withUnknownCv_throwsCvNotFoundException() {
        CvId cvId = CvId.generate();
        when(cvDocumentRepository.findById(cvId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retrieveContext(cvId, "query", 3))
                .isInstanceOf(CvNotFoundException.class);
    }
}
