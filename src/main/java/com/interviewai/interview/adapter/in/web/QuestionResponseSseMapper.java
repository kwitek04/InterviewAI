package com.interviewai.interview.adapter.in.web;

import com.interviewai.interview.domain.QuestionResponseEventType;
import com.interviewai.interview.domain.QuestionResponseStreamEvent;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class QuestionResponseSseMapper {

    SseEmitter.SseEventBuilder toSseEvent(QuestionResponseStreamEvent event) {
        return SseEmitter.event()
                .id(Integer.toString(event.sequence()))
                .name(toEventName(event.type()))
                .data(toPayload(event), MediaType.APPLICATION_JSON);
    }

    private String toEventName(QuestionResponseEventType type) {
        return switch (type) {
            case TOKEN -> "token";
            case COMPLETED -> "completed";
            case ERROR -> "error";
        };
    }

    private Object toPayload(QuestionResponseStreamEvent event) {
        return switch (event.type()) {
            case TOKEN -> new TokenEventPayload(event.payload());
            case COMPLETED -> new CompletedEventPayload(event.payload());
            case ERROR -> new ErrorEventPayload(event.payload());
        };
    }

    static boolean isTerminal(QuestionResponseStreamEvent event) {
        return event.type() == QuestionResponseEventType.COMPLETED
                || event.type() == QuestionResponseEventType.ERROR;
    }

    private record TokenEventPayload(String text) {
    }

    private record CompletedEventPayload(String question) {
    }

    private record ErrorEventPayload(String message) {
    }
}
