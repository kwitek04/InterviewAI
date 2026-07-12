package com.interviewai.session.adapter.in.web;

import com.interviewai.session.application.AcceptedGeneration;

import java.util.UUID;

/**
 * Response returned when a command accepts a new interviewer response generation.
 */
record AcceptedGenerationResponse(UUID sessionId, UUID responseId) {

    static AcceptedGenerationResponse from(AcceptedGeneration accepted) {
        return new AcceptedGenerationResponse(accepted.sessionId().value(), accepted.responseId().value());
    }
}
