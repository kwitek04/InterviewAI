package com.interviewai.cv.adapter.out.embedding;

import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the LangChain4j Ollama embedding model used to generate CV vectors.
 */
@Configuration
@EnableConfigurationProperties(CvEmbeddingProperties.class)
class OllamaEmbeddingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingConfiguration.class);

    @Bean
    OllamaEmbeddingModel ollamaEmbeddingModel(CvEmbeddingProperties properties) {
        log.info(
                "Configuring Ollama embedding model with baseUrl='{}', embeddingModelName='{}', timeout='{}'",
                properties.baseUrl(),
                properties.embeddingModelName(),
                properties.timeout());
        return OllamaEmbeddingModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(properties.embeddingModelName())
                .timeout(properties.timeout())
                .build();
    }
}
