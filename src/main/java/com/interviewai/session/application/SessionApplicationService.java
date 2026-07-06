package com.interviewai.session.application;

import com.interviewai.interview.application.port.out.QuestionGenerator;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.shared.SessionId;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * Orchestrates the interview session use cases: starting a session, recording a
 * candidate's answer, and advancing the conversation with the next question.
 * <p>
 * Coordinates the pure {@link InterviewSession} state machine with the
 * {@link QuestionGenerator} and {@link SessionRepository} ports; it holds no
 * business rules of its own.
 */
@Service
public class SessionApplicationService {

    private final SessionRepository sessionRepository;
    private final QuestionGenerator questionGenerator;
    private final Clock clock;

    public SessionApplicationService(
            SessionRepository sessionRepository, QuestionGenerator questionGenerator, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.questionGenerator = questionGenerator;
        this.clock = clock;
    }

    /**
     * Starts a new interview session and asks the first question.
     */
    public InterviewSession startInterview() {
        InterviewSession session = InterviewSession.create(SessionId.generate())
                .apply(new SessionCommand.StartInterview());
        return askNextQuestion(session);
    }

    /**
     * Records the candidate's answer for the given session and asks the next question.
     *
     * @throws SessionNotFoundException      if no session exists for the given id
     * @throws com.interviewai.session.domain.SessionTransitionException if the session
     *         is not currently awaiting an answer
     */
    public InterviewSession submitAnswer(SessionId id, String answer) {
        InterviewSession session = loadOrThrow(id)
                .apply(new SessionCommand.SubmitAnswer(answer, clock.instant()));
        return askNextQuestion(session);
    }

    /**
     * Returns the current state and transcript of the given session.
     *
     * @throws SessionNotFoundException if no session exists for the given id
     */
    public InterviewSession getSession(SessionId id) {
        return loadOrThrow(id);
    }

    private InterviewSession askNextQuestion(InterviewSession session) {
        String question = questionGenerator.generateNextQuestion(session.transcript());
        InterviewSession updated = session.apply(new SessionCommand.AskQuestion(question, clock.instant()));
        sessionRepository.save(updated);
        return updated;
    }

    private InterviewSession loadOrThrow(SessionId id) {
        return sessionRepository.findById(id).orElseThrow(() -> new SessionNotFoundException(id));
    }
}
