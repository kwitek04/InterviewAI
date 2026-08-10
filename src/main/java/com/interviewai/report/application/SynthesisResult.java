package com.interviewai.report.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validated strengths / weaknesses / recommendations synthesis.
 */
public record SynthesisResult(List<String> strengths, List<String> weaknesses, List<String> recommendations) {

    public SynthesisResult {
        strengths = normalizeRequiredList(strengths, "strengths");
        weaknesses = normalizeRequiredList(weaknesses, "weaknesses");
        recommendations = normalizeRequiredList(recommendations, "recommendations");
    }

    public static SynthesisResult validated(
            List<String> strengths, List<String> weaknesses, List<String> recommendations) {
        return new SynthesisResult(strengths, weaknesses, recommendations);
    }

    private static List<String> normalizeRequiredList(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        Set<String> unique = new LinkedHashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new InvalidReportContentException(fieldName + " must not contain blank entries");
            }
            String trimmed = value.trim();
            if (unique.add(trimmed)) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            throw new InvalidReportContentException(fieldName + " must not be empty");
        }
        return List.copyOf(normalized);
    }
}
