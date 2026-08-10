package com.interviewai.report.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Generation settings for interview reports.
 */
@ConfigurationProperties(prefix = "interviewai.report.generation")
public record ReportGenerationProperties(int maxAttempts) {

    public ReportGenerationProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
    }
}
