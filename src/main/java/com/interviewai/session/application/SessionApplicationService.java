package com.interviewai.session.application;

import com.interviewai.cv.application.CvRetrievalService;
import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.interview.application.port.out.QuestionGenerator;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.shared.CvId;
import com.interviewai.shared.SessionId;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

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
    private final CvRetrievalService cvRetrievalService;
    private final Clock clock;

    public SessionApplicationService(
            SessionRepository sessionRepository,
            QuestionGenerator questionGenerator,
            CvRetrievalService cvRetrievalService,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.questionGenerator = questionGenerator;
        this.cvRetrievalService = cvRetrievalService;
        this.clock = clock;
    }

    /**
     * Starts a new interview session and asks the first question.
     */
    public InterviewSession startInterview(Optional<CvId> cvId) {
        InterviewSession session = cvId.map(id -> InterviewSession.create(SessionId.generate(), id))
                .orElseGet(() -> InterviewSession.create(SessionId.generate()))
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

    /**
     * Ends the given session, marking the interview as completed.
     *
     * @throws SessionNotFoundException      if no session exists for the given id
     * @throws com.interviewai.session.domain.SessionTransitionException if the session
     *         is not currently awaiting an answer
     */
    public InterviewSession endInterview(SessionId id) {
        InterviewSession session = loadOrThrow(id).apply(new SessionCommand.EndInterview());
        sessionRepository.save(session);
        return session;
    }

    /**
     * Cancels the given session.
     *
     * @throws SessionNotFoundException      if no session exists for the given id
     * @throws com.interviewai.session.domain.SessionTransitionException if the session
     *         has already ended or been cancelled
     */
    public InterviewSession cancelInterview(SessionId id) {
        InterviewSession session = loadOrThrow(id).apply(new SessionCommand.CancelInterview());
        sessionRepository.save(session);
        return session;
    }

    private InterviewSession askNextQuestion(InterviewSession session) {
        InterviewContext context = session.cvId()
                .map(cvId -> buildInterviewContext(cvId, session))
                .orElseGet(InterviewContext::empty);

        String question = questionGenerator.generateNextQuestion(session.transcript(), context);
        InterviewSession updated = session.apply(new SessionCommand.AskQuestion(question, clock.instant()));
        sessionRepository.save(updated);
        return updated;
    }

    private InterviewContext buildInterviewContext(CvId cvId, InterviewSession session) {
        String query = lastCandidateAnswer(session)
                .orElseGet(() -> cvRetrievalService.retrieveJobOffer(cvId));
        return toInterviewContext(cvRetrievalService.retrieveContext(cvId, query, 4));
    }

    private Optional<String> lastCandidateAnswer(InterviewSession session) {
        return session.transcript().messages().stream()
                .filter(message -> message.role() == MessageRole.CANDIDATE)
                .reduce((first, second) -> second)
                .map(message -> message.content());
    }

    private InterviewContext toInterviewContext(CvRetrievalService.CvContext context) {
        return new InterviewContext(context.jobOffer(), context.relevantChunks());
    }

    private InterviewSession loadOrThrow(SessionId id) {
        return sessionRepository.findById(id).orElseThrow(() -> new SessionNotFoundException(id));
    }
}
