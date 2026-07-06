package com.interviewai.interview.adapter.out.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Connection settings for the local Ollama server, bound from
 * {@code interviewai.llm.ollama.*} configuration properties.
 */
@ConfigurationProperties(prefix = "interviewai.llm.ollama")
record OllamaProperties(String baseUrl, String modelName, Duration timeout) {
}
