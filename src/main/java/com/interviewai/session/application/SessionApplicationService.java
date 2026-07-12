package com.interviewai.session.application;

import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.shared.CvId;
import com.interviewai.shared.SessionId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Orchestrates interview session use cases and accepts asynchronous question generation.
 */
@Service
public class SessionApplicationService {

    private final SessionRepository sessionRepository;
    private final QuestionResponseStore questionResponseStore;
    private final QuestionGenerationCoordinator questionGenerationCoordinator;
    private final TransactionTemplate transactionTemplate;
    private final Executor questionGenerationExecutor;
    private final Clock clock;

    public SessionApplicationService(
            SessionRepository sessionRepository,
            QuestionResponseStore questionResponseStore,
            QuestionGenerationCoordinator questionGenerationCoordinator,
            TransactionTemplate transactionTemplate,
            @Qualifier("questionGenerationExecutor") Executor questionGenerationExecutor,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.questionResponseStore = questionResponseStore;
        this.questionGenerationCoordinator = questionGenerationCoordinator;
        this.transactionTemplate = transactionTemplate;
        this.questionGenerationExecutor = questionGenerationExecutor;
        this.clock = clock;
    }

    /**
     * Starts a new interview session and accepts generation of the first question.
     */
    public AcceptedGeneration startInterview(Optional<CvId> cvId) {
        SessionId sessionId = SessionId.generate();
        InterviewSession session = cvId.map(id -> InterviewSession.create(sessionId, id))
                .orElseGet(() -> InterviewSession.create(sessionId))
                .apply(new SessionCommand.StartInterview());

        AcceptedGeneration accepted = transactionTemplate.execute(status -> {
            sessionRepository.save(session);
            return new AcceptedGeneration(sessionId, questionResponseStore.createPending(sessionId).id());
        });

        questionGenerationExecutor.execute(() -> questionGenerationCoordinator.generate(accepted));
        return accepted;
    }

    /**
     * Records the candidate's answer and accepts generation of the next question.
     */
    public AcceptedGeneration submitAnswer(SessionId id, String answer) {
        InterviewSession session = loadOrThrow(id)
                .apply(new SessionCommand.SubmitAnswer(answer, clock.instant()));

        AcceptedGeneration accepted = transactionTemplate.execute(status -> {
            sessionRepository.save(session);
            return new AcceptedGeneration(id, questionResponseStore.createPending(id).id());
        });

        questionGenerationExecutor.execute(() -> questionGenerationCoordinator.generate(accepted));
        return accepted;
    }

    /**
     * Returns the current state and transcript of the given session.
     */
    public InterviewSession getSession(SessionId id) {
        return loadOrThrow(id);
    }

    /**
     * Ends the given session, marking the interview as completed.
     */
    public InterviewSession endInterview(SessionId id) {
        InterviewSession session = loadOrThrow(id).apply(new SessionCommand.EndInterview());
        sessionRepository.save(session);
        return session;
    }

    /**
     * Cancels the given session and any active generated response.
     */
    public InterviewSession cancelInterview(SessionId id) {
        InterviewSession session = loadOrThrow(id).apply(new SessionCommand.CancelInterview());
        sessionRepository.save(session);
        questionResponseStore.findActiveBySessionId(id)
                .ifPresent(response -> questionResponseStore.markCancelled(response.id()));
        return session;
    }

    private InterviewSession loadOrThrow(SessionId id) {
        return sessionRepository.findById(id).orElseThrow(() -> new SessionNotFoundException(id));
    }
}
