package com.interviewai.interview.adapter.out.llm;

import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Buffers only the initial assistant/interviewer prefix decision, then forwards partial responses.
 */
final class StreamingQuestionPrefixFilter {

    private static final Pattern ROLE_PREFIX = Pattern.compile("(?is)^(assistant|interviewer)\\s*:?\\s*");

    private final Consumer<String> downstream;
    private final StringBuilder prefixBuffer = new StringBuilder();
    private final StringBuilder emitted = new StringBuilder();
    private boolean resolved;

    StreamingQuestionPrefixFilter(Consumer<String> downstream) {
        this.downstream = downstream;
    }

    void accept(String partialResponse) {
        if (resolved) {
            emit(partialResponse);
            return;
        }

        prefixBuffer.append(partialResponse);
        resolvePrefixBuffer();
    }

    void finish() {
        if (resolved || prefixBuffer.isEmpty()) {
            return;
        }

        String sanitized = QuestionTextSanitizer.sanitizeQuestion(prefixBuffer.toString());
        if (sanitized != null && !sanitized.isEmpty()) {
            emit(sanitized);
        }
        resolved = true;
    }

    String emittedText() {
        return emitted.toString();
    }

    private void resolvePrefixBuffer() {
        String candidate = prefixBuffer.toString().stripLeading();
        if (candidate.isEmpty()) {
            return;
        }

        Matcher matcher = ROLE_PREFIX.matcher(candidate);
        if (matcher.lookingAt()) {
            String afterRole = candidate.substring(matcher.end());
            if (afterRole.isEmpty() || afterRole.matches("[\\s:]*")) {
                return;
            }

            String remainder = afterRole.replaceFirst("^(\\r?\\n)+", "").stripLeading();
            resolved = true;
            emit(remainder);
            return;
        }

        if (couldStillBeRolePrefix(candidate)) {
            return;
        }

        resolved = true;
        emit(candidate);
    }

    private static boolean couldStillBeRolePrefix(String candidate) {
        String lower = candidate.toLowerCase();
        return "assistant".startsWith(lower) || "interviewer".startsWith(lower);
    }

    private void emit(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        emitted.append(text);
        downstream.accept(text);
    }
}
