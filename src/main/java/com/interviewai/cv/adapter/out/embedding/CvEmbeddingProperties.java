package com.interviewai.cv.adapter.out.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection settings for the local Ollama embedding model, bound from
 * {@code interviewai.llm.ollama.*} configuration properties.
 */
@ConfigurationProperties(prefix = "interviewai.llm.ollama")
record CvEmbeddingProperties(String baseUrl, String embeddingModelName, Duration timeout) {
}
