package com.interviewai.cv.application;

import com.interviewai.cv.application.port.out.CvChunkStore;
import com.interviewai.cv.application.port.out.CvDocumentRepository;
import com.interviewai.cv.application.port.out.EmbeddingGenerator;
import com.interviewai.cv.application.port.out.ScoredChunk;
import com.interviewai.cv.domain.CvDocument;
import com.interviewai.shared.CvId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Public retrieval API for other modules. Loads a CV's job offer and the chunks
 * most relevant to a query string.
 */
@Service
public class CvRetrievalService {

    /**
     * Retrieved job-offer text together with the most relevant CV excerpts.
     */
    public record CvContext(String jobOffer, List<String> relevantChunks) {
    }

    private final CvDocumentRepository cvDocumentRepository;
    private final EmbeddingGenerator embeddingGenerator;
    private final CvChunkStore cvChunkStore;

    public CvRetrievalService(
            CvDocumentRepository cvDocumentRepository,
            EmbeddingGenerator embeddingGenerator,
            CvChunkStore cvChunkStore) {
        this.cvDocumentRepository = cvDocumentRepository;
        this.embeddingGenerator = embeddingGenerator;
        this.cvChunkStore = cvChunkStore;
    }

    /**
     * Returns the stored job offer for the given CV.
     *
     * @throws CvNotFoundException if no CV document exists for the given id
     */
    public String retrieveJobOffer(CvId cvId) {
        return cvDocumentRepository.findById(cvId)
                .orElseThrow(() -> new CvNotFoundException(cvId))
                .jobOffer();
    }

    /**
     * Returns the job offer and the CV excerpts most similar to the given query.
     *
     * @throws CvNotFoundException if no CV document exists for the given id
     */
    public CvContext retrieveContext(CvId cvId, String query, int maxChunks) {
        CvDocument document = cvDocumentRepository.findById(cvId)
                .orElseThrow(() -> new CvNotFoundException(cvId));

        float[] queryEmbedding = embeddingGenerator.embed(query);
        List<String> relevantChunks = cvChunkStore.findMostSimilar(cvId, queryEmbedding, maxChunks).stream()
                .map(ScoredChunk::content)
                .toList();

        return new CvContext(document.jobOffer(), relevantChunks);
    }
}
