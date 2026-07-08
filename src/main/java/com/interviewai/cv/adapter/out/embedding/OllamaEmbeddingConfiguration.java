package com.interviewai.cv.adapter.out.embedding;

import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the LangChain4j Ollama embedding model used to generate CV vectors.
 */
@Configuration
@EnableConfigurationProperties(CvEmbeddingProperties.class)
class OllamaEmbeddingConfiguration {

    @Bean
    OllamaEmbeddingModel ollamaEmbeddingModel(CvEmbeddingProperties properties) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(properties.embeddingModelName())
                .timeout(properties.timeout())
                .build();
    }
}
