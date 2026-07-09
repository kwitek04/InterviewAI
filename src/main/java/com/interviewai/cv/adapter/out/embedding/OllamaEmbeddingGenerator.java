package com.interviewai.cv.adapter.out.embedding;

import com.interviewai.cv.application.CvEmbeddingException;
import com.interviewai.cv.application.port.out.EmbeddingGenerator;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Embedding generator backed by the local Ollama server via LangChain4j.
 */
@Component
class OllamaEmbeddingGenerator implements EmbeddingGenerator {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingGenerator.class);

    private final OllamaEmbeddingModel embeddingModel;
    private final CvEmbeddingProperties properties;

    OllamaEmbeddingGenerator(OllamaEmbeddingModel embeddingModel, CvEmbeddingProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        try {
            return embeddingModel.embedAll(segments).content().stream()
                    .map(Embedding::vector)
                    .map(float[]::clone)
                    .toList();
        } catch (ModelNotFoundException modelNotFound) {
            log.error(
                    "Ollama embedding model not found. configuredBaseUrl='{}', configuredModel='{}', originalError='{}'",
                    properties.baseUrl(),
                    embeddingModel.modelName(),
                    modelNotFound.getMessage(),
                    modelNotFound);
            throw new CvEmbeddingException(
                    "Embedding model is unavailable in Ollama. Verify configured model '" + embeddingModel.modelName()
                            + "' is available in Ollama.",
                    modelNotFound);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to generate embeddings with Ollama. configuredBaseUrl='{}', configuredModel='{}', originalError='{}'",
                    properties.baseUrl(),
                    embeddingModel.modelName(),
                    exception.getMessage(),
                    exception);
            throw new CvEmbeddingException("Failed to generate embeddings with Ollama", exception);
        }
    }
}
