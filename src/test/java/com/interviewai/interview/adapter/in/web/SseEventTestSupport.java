package com.interviewai.interview.adapter.in.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class SseEventTestSupport {

    private SseEventTestSupport() {
    }

    record CapturedSseEvent(String id, String eventName, String jsonPayload) {
    }

    static CapturedSseEvent capture(SseEmitter.SseEventBuilder builder, ObjectMapper objectMapper)
            throws JsonProcessingException {
        String metadata = null;
        Object payload = null;

        for (ResponseBodyEmitter.DataWithMediaType part : builder.build()) {
            Object data = part.getData();
            if (data instanceof String text && text.contains("event:")) {
                metadata = text;
            } else if (!(data instanceof String)) {
                payload = data;
            }
        }

        return new CapturedSseEvent(
                lineValue(metadata, "id:"),
                lineValue(metadata, "event:"),
                objectMapper.writeValueAsString(payload));
    }

    private static String lineValue(String metadata, String prefix) {
        if (metadata == null) {
            throw new IllegalStateException("Missing SSE metadata for " + prefix);
        }
        for (String line : metadata.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        throw new IllegalStateException("Missing SSE line " + prefix);
    }
}
