package com.interviewai.interview.adapter.in.web;

import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.interview.domain.QuestionResponse;
import com.interviewai.interview.domain.QuestionResponseStatus;
import com.interviewai.interview.domain.QuestionResponseStreamEvent;
import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

@Component
class QuestionResponseStreamFeeder {

    private final QuestionResponseStore questionResponseStore;
    private final QuestionResponseSseMapper sseMapper;
    private final StreamingProperties properties;
    private final Executor questionGenerationExecutor;
    private final Clock clock;

    QuestionResponseStreamFeeder(
            QuestionResponseStore questionResponseStore,
            QuestionResponseSseMapper sseMapper,
            StreamingProperties properties,
            @Qualifier("questionGenerationExecutor") Executor questionGenerationExecutor,
            Clock clock) {
        this.questionResponseStore = questionResponseStore;
        this.sseMapper = sseMapper;
        this.properties = properties;
        this.questionGenerationExecutor = questionGenerationExecutor;
        this.clock = clock;
    }

    SseEmitter open(SessionId sessionId, ResponseId responseId, int afterSequence, HttpServletResponse response) {
        questionResponseStore.requireOwnedBySession(responseId, sessionId);

        SseEmitter emitter = new SseEmitter(properties.emitterTimeout().toMillis());
        questionGenerationExecutor.execute(() -> deliverEvents(emitter, responseId, afterSequence, response));
        return emitter;
    }

    void deliverEvents(SseEmitter emitter, ResponseId responseId, int afterSequence, HttpServletResponse response) {
        int lastDeliveredSequence = afterSequence;
        Instant lastActivity = clock.instant();

        try {
            while (!Thread.currentThread().isInterrupted()) {
                List<QuestionResponseStreamEvent> events =
                        questionResponseStore.eventsAfter(responseId, lastDeliveredSequence);

                if (!events.isEmpty()) {
                    QuestionResponseStreamEvent event = events.getFirst();
                    emitter.send(sseMapper.toSseEvent(event));
                    flushResponse(response);
                    lastDeliveredSequence = event.sequence();
                    lastActivity = clock.instant();
                    if (QuestionResponseSseMapper.isTerminal(event)) {
                        emitter.complete();
                        return;
                    }
                    continue;
                }

                if (isTerminalWithoutPendingEvents(responseId, lastDeliveredSequence)) {
                    emitter.complete();
                    return;
                }

                if (Duration.between(lastActivity, clock.instant()).compareTo(properties.heartbeatInterval()) >= 0) {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                    flushResponse(response);
                    lastActivity = clock.instant();
                }

                Thread.sleep(properties.pollInterval().toMillis());
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            emitter.completeWithError(exception);
        }
    }

    private void flushResponse(HttpServletResponse response) throws IOException {
        if (response != null) {
            response.flushBuffer();
        }
    }

    private boolean isTerminalWithoutPendingEvents(ResponseId responseId, int lastDeliveredSequence) {
        return questionResponseStore.findById(responseId)
                .map(QuestionResponse::status)
                .filter(QuestionResponseStatus::isTerminal)
                .isPresent()
                && questionResponseStore.eventsAfter(responseId, lastDeliveredSequence).isEmpty();
    }
}
