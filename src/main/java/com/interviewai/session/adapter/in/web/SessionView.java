package com.interviewai.session.adapter.in.web;

import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionState;

import java.util.List;
import java.util.UUID;

/**
 * The full state and transcript of an interview session, as exposed over the REST API.
 */
record SessionView(UUID sessionId, String state, List<MessageView> transcript) {

    static SessionView from(InterviewSession session) {
        List<MessageView> messages = session.transcript().messages().stream()
                .map(MessageView::from)
                .toList();
        return new SessionView(session.id().value(), stateName(session.state()), messages);
    }

    private static String stateName(SessionState state) {
        return switch (state) {
            case SessionState.Created() -> "CREATED";
            case SessionState.InProgress() -> "IN_PROGRESS";
            case SessionState.AwaitingAnswer() -> "AWAITING_ANSWER";
            case SessionState.Completed() -> "COMPLETED";
            case SessionState.Cancelled() -> "CANCELLED";
        };
    }
}
