package com.interviewai.interview.adapter.in.web;

import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * SSE endpoint for replaying stored interviewer response events.
 */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/responses/{responseId}")
class InterviewStreamController {

    private final QuestionResponseStreamFeeder streamFeeder;

    InterviewStreamController(QuestionResponseStreamFeeder streamFeeder) {
        this.streamFeeder = streamFeeder;
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter streamEvents(
            @PathVariable UUID sessionId,
            @PathVariable UUID responseId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        int afterSequence = LastEventIdParser.parse(lastEventId);
        return streamFeeder.open(new SessionId(sessionId), new ResponseId(responseId), afterSequence);
    }
}
