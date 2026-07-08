package com.interviewai.cv.adapter.out.embedding;

import com.interviewai.cv.application.port.out.EmbeddingGenerator;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Embedding generator backed by the local Ollama server via LangChain4j.
 */
@Component
class OllamaEmbeddingGenerator implements EmbeddingGenerator {

    private final OllamaEmbeddingModel embeddingModel;

    OllamaEmbeddingGenerator(OllamaEmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();
        return embeddingModel.embedAll(segments).content().stream()
                .map(Embedding::vector)
                .map(float[]::clone)
                .toList();
    }
}
