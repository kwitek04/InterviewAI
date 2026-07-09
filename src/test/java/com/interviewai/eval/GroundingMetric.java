package com.interviewai.eval;

import java.util.List;

/**
 * Literal fact-matching metric for eval: a question is grounded when it contains
 * at least one fixture fact as a case-insensitive substring.
 */
public final class GroundingMetric {

    private GroundingMetric() {
    }

    public static boolean isGrounded(String question, List<String> facts) {
        return findMatchedFact(question, facts) != null;
    }

    public static String findMatchedFact(String question, List<String> facts) {
        if (question == null || facts == null) {
            return null;
        }
        String lowerQuestion = question.toLowerCase();
        return facts.stream()
                .filter(fact -> fact != null && lowerQuestion.contains(fact.toLowerCase()))
                .findFirst()
                .orElse(null);
    }
}
