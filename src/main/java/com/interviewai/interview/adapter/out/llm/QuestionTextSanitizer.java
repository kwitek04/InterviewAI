package com.interviewai.interview.adapter.out.llm;

final class QuestionTextSanitizer {

    private QuestionTextSanitizer() {
    }

    static String sanitizeQuestion(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.stripLeading()
                .replaceFirst("(?is)^(assistant|interviewer)\\s*:?\\s*(\\r?\\n)+", "")
                .replaceFirst("(?is)^(assistant|interviewer)\\s*:?\\s*", "")
                .strip();
    }
}
