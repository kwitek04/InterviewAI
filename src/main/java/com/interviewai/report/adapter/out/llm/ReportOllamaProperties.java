package com.interviewai.report.adapter.out.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Ollama connection settings dedicated to report generation.
 */
@ConfigurationProperties(prefix = "interviewai.report.llm")
record ReportOllamaProperties(String baseUrl, String modelName, Duration timeout) {
}
