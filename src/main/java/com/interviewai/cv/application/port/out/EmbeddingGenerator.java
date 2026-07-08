package com.interviewai.cv.application.port.out;

import java.util.List;

/**
 * Generates dense vector embeddings for free-form text.
 */
public interface EmbeddingGenerator {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
