package com.interviewai.cv.application.port.out;

import com.interviewai.shared.CvId;

import java.util.List;

/**
 * Stores embedded CV chunks and retrieves the most similar chunks for a query
 * vector.
 */
public interface CvChunkStore {

    void saveAll(CvId cvId, List<EmbeddedChunk> chunks);

    List<ScoredChunk> findMostSimilar(CvId cvId, float[] queryEmbedding, int limit);
}
