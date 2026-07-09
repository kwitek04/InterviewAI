package com.interviewai.session.adapter.in.web;

import com.interviewai.session.application.SessionApplicationService;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.shared.CvId;
import com.interviewai.shared.SessionId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * REST API for driving an AI-simulated interview conversation.
 */
@RestController
@RequestMapping("/api/v1/sessions")
class SessionController {

    private final SessionApplicationService sessionApplicationService;

    SessionController(SessionApplicationService sessionApplicationService) {
        this.sessionApplicationService = sessionApplicationService;
    }

    /**
     * Starts a new interview session and returns its first question.
     */
    @PostMapping
    ResponseEntity<QuestionResponse> startSession(@RequestBody(required = false) StartSessionRequest request) {
        Optional<CvId> cvId = request == null || request.cvId() == null
                ? Optional.empty()
                : Optional.of(new CvId(request.cvId()));
        InterviewSession session = sessionApplicationService.startInterview(cvId);
        return ResponseEntity.status(HttpStatus.CREATED).body(QuestionResponse.from(session));
    }

    /**
     * Submits the candidate's answer to the current question and returns the next one.
     */
    @PostMapping("/{id}/answers")
    QuestionResponse submitAnswer(@PathVariable UUID id, @Valid @RequestBody SubmitAnswerRequest request) {
        InterviewSession session = sessionApplicationService.submitAnswer(new SessionId(id), request.answer());
        return QuestionResponse.from(session);
    }

    /**
     * Returns the current state and full transcript of a session.
     */
    @GetMapping("/{id}")
    SessionView getSession(@PathVariable UUID id) {
        InterviewSession session = sessionApplicationService.getSession(new SessionId(id));
        return SessionView.from(session);
    }

    /**
     * Ends the interview, marking the session as completed.
     */
    @PostMapping("/{id}/end")
    SessionView endSession(@PathVariable UUID id) {
        InterviewSession session = sessionApplicationService.endInterview(new SessionId(id));
        return SessionView.from(session);
    }

    /**
     * Cancels the interview.
     */
    @PostMapping("/{id}/cancel")
    SessionView cancelSession(@PathVariable UUID id) {
        InterviewSession session = sessionApplicationService.cancelInterview(new SessionId(id));
        return SessionView.from(session);
    }
}
