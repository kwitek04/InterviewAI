package com.interviewai.interview.application.port.out;

import com.interviewai.interview.domain.QuestionResponse;
import com.interviewai.interview.domain.QuestionResponseStreamEvent;
import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;

import java.util.List;
import java.util.Optional;

/**
 * Durable store for generated interviewer responses and their ordered stream events.
 */
public interface QuestionResponseStore {

    QuestionResponse createPending(SessionId sessionId);

    Optional<QuestionResponse> findById(ResponseId responseId);

    Optional<QuestionResponse> findActiveBySessionId(SessionId sessionId);

    QuestionResponse requireOwnedBySession(ResponseId responseId, SessionId sessionId);

    void markStreaming(ResponseId responseId);

    QuestionResponseStreamEvent appendTokenEvent(ResponseId responseId, String tokenText);

    QuestionResponseStreamEvent markCompleted(ResponseId responseId, String finalQuestion);

    QuestionResponseStreamEvent markFailed(ResponseId responseId, String safeMessage);

    void markCancelled(ResponseId responseId);

    List<QuestionResponseStreamEvent> eventsAfter(ResponseId responseId, int afterSequence);
}
