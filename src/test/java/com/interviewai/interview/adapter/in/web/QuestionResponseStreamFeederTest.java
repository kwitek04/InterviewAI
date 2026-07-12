package com.interviewai.interview.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewai.interview.application.QuestionResponseNotFoundException;
import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.interview.domain.QuestionResponse;
import com.interviewai.interview.domain.QuestionResponseEventType;
import com.interviewai.interview.domain.QuestionResponseStatus;
import com.interviewai.interview.domain.QuestionResponseStreamEvent;
import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionResponseStreamFeederTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private QuestionResponseStore questionResponseStore;

    private QuestionResponseStreamFeeder feeder;
    private SessionId sessionId;
    private ResponseId responseId;

    @BeforeEach
    void setUp() {
        sessionId = SessionId.generate();
        responseId = ResponseId.generate();
        feeder = new QuestionResponseStreamFeeder(
                questionResponseStore,
                new QuestionResponseSseMapper(),
                new StreamingProperties(Duration.ofMinutes(1), Duration.ofMillis(1), Duration.ofHours(1)),
                Runnable::run,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("open verifies session ownership before returning an emitter")
    void open_verifiesSessionOwnership() {
        QuestionResponseStreamFeeder ownershipFeeder = new QuestionResponseStreamFeeder(
                questionResponseStore,
                new QuestionResponseSseMapper(),
                new StreamingProperties(Duration.ofMinutes(1), Duration.ofMillis(1), Duration.ofHours(1)),
                runnable -> {},
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(questionResponseStore.requireOwnedBySession(responseId, sessionId))
                .thenReturn(activeResponse());

        ownershipFeeder.open(sessionId, responseId, 0);

        verify(questionResponseStore).requireOwnedBySession(responseId, sessionId);
    }

    @Test
    @DisplayName("open rejects unknown or mismatched responses")
    void open_unknownResponse_throws() {
        when(questionResponseStore.requireOwnedBySession(responseId, sessionId))
                .thenThrow(new QuestionResponseNotFoundException(responseId));

        assertThatThrownBy(() -> feeder.open(sessionId, responseId, 0))
                .isInstanceOf(QuestionResponseNotFoundException.class);
    }

    @Test
    @DisplayName("deliverEvents replays all stored events in order and completes on terminal event")
    void deliverEvents_replaysEventsInOrder() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        when(questionResponseStore.eventsAfter(responseId, 0)).thenReturn(List.of(
                streamEvent(1, QuestionResponseEventType.TOKEN, "How"),
                streamEvent(2, QuestionResponseEventType.TOKEN, " are you?"),
                streamEvent(3, QuestionResponseEventType.COMPLETED, "How are you?")));

        feeder.deliverEvents(emitter, responseId, 0);

        assertThat(emitter.eventNames()).containsExactly("token", "token", "completed");
        assertThat(emitter.eventIds()).containsExactly("1", "2", "3");
        assertThat(emitter.payloads()).containsExactly(
                "{\"text\":\"How\"}",
                "{\"text\":\" are you?\"}",
                "{\"question\":\"How are you?\"}");
        assertThat(emitter.completed()).isTrue();
    }

    @Test
    @DisplayName("deliverEvents starts replay after Last-Event-ID sequence")
    void deliverEvents_replaysOnlyAfterLastEventId() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        when(questionResponseStore.eventsAfter(responseId, 2)).thenReturn(List.of(
                streamEvent(3, QuestionResponseEventType.COMPLETED, "How are you?")));

        feeder.deliverEvents(emitter, responseId, 2);

        assertThat(emitter.eventIds()).containsExactly("3");
        assertThat(emitter.completed()).isTrue();
    }

    @Test
    @DisplayName("deliverEvents completes when response is terminal and no events remain")
    void deliverEvents_whenAlreadyTerminal_completesWithoutDuplicateEvents() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        when(questionResponseStore.eventsAfter(responseId, 3)).thenReturn(List.of());
        when(questionResponseStore.findById(responseId)).thenReturn(Optional.of(completedResponse()));

        feeder.deliverEvents(emitter, responseId, 3);

        assertThat(emitter.sentEvents()).isZero();
        assertThat(emitter.completed()).isTrue();
    }

    @Test
    @DisplayName("deliverEvents does not mutate response status on transport failure")
    void deliverEvents_onSendFailure_doesNotMutateResponseStatus() throws Exception {
        CapturingSseEmitter emitter = new CapturingSseEmitter(true);
        when(questionResponseStore.eventsAfter(responseId, 0)).thenReturn(List.of(
                streamEvent(1, QuestionResponseEventType.TOKEN, "Hello")));

        feeder.deliverEvents(emitter, responseId, 0);

        verify(questionResponseStore, never()).markFailed(eq(responseId), org.mockito.ArgumentMatchers.anyString());
        verify(questionResponseStore, never()).markCancelled(responseId);
        verify(questionResponseStore, never()).markCompleted(eq(responseId), org.mockito.ArgumentMatchers.anyString());
    }

    private QuestionResponse activeResponse() {
        return new QuestionResponse(
                responseId, sessionId, QuestionResponseStatus.STREAMING, "", null, 0, null, NOW, NOW);
    }

    private QuestionResponse completedResponse() {
        return new QuestionResponse(
                responseId,
                sessionId,
                QuestionResponseStatus.COMPLETED,
                "Done",
                "Done",
                3,
                null,
                NOW,
                NOW);
    }

    private QuestionResponseStreamEvent streamEvent(int sequence, QuestionResponseEventType type, String payload) {
        return new QuestionResponseStreamEvent(responseId, sequence, type, payload, NOW);
    }

    private static final class CapturingSseEmitter extends SseEmitter {

        private final List<String> eventNames = new ArrayList<>();
        private final List<String> eventIds = new ArrayList<>();
        private final List<String> payloads = new ArrayList<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private final boolean failOnSend;

        private CapturingSseEmitter() {
            this(false);
        }

        private CapturingSseEmitter(boolean failOnSend) {
            super(60_000L);
            this.failOnSend = failOnSend;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("client disconnected");
            }
            try {
                SseEventTestSupport.CapturedSseEvent captured =
                        SseEventTestSupport.capture(builder, new ObjectMapper());
                eventIds.add(captured.id());
                eventNames.add(captured.eventName());
                payloads.add(captured.jsonPayload());
            } catch (IOException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IOException(exception);
            }
        }

        @Override
        public void complete() {
            completed.set(true);
        }

        List<String> eventNames() {
            return eventNames;
        }

        List<String> eventIds() {
            return eventIds;
        }

        List<String> payloads() {
            return payloads;
        }

        int sentEvents() {
            return eventNames.size();
        }

        boolean completed() {
            return completed.get();
        }
    }
}
