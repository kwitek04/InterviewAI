package com.interviewai.session.adapter.in.web;

import com.interviewai.session.domain.InterviewSession;

import java.util.UUID;

/**
 * The interviewer's latest question for a session, returned after starting an
 * interview or submitting an answer.
 */
record QuestionResponse(UUID sessionId, String question) {

    static QuestionResponse from(InterviewSession session) {
        String question = session.transcript().messages().getLast().content();
        return new QuestionResponse(session.id().value(), question);
    }
}
