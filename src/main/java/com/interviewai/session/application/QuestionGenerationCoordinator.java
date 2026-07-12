package com.interviewai.session.application;

import com.interviewai.cv.application.CvRetrievalService;
import com.interviewai.interview.application.InvalidQuestionResponseStatusTransitionException;
import com.interviewai.interview.application.QuestionGenerationException;
import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.interview.application.port.out.QuestionResponseStore;
import com.interviewai.interview.application.port.out.StreamingQuestionGenerator;
import com.interviewai.interview.domain.QuestionResponse;
import com.interviewai.interview.domain.QuestionResponseStatus;
import com.interviewai.session.application.port.out.SessionRepository;
import com.interviewai.session.domain.InterviewSession;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.SessionCommand;
import com.interviewai.session.domain.SessionState;
import com.interviewai.shared.CvId;
import com.interviewai.shared.ResponseId;
import com.interviewai.shared.SessionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

@Component
class QuestionGenerationCoordinator {

    static final String SAFE_FAILURE_MESSAGE = "Question generation failed.";

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerationCoordinator.class);

    private final SessionRepository sessionRepository;
    private final QuestionResponseStore questionResponseStore;
    private final StreamingQuestionGenerator streamingQuestionGenerator;
    private final CvRetrievalService cvRetrievalService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    QuestionGenerationCoordinator(
            SessionRepository sessionRepository,
            QuestionResponseStore questionResponseStore,
            StreamingQuestionGenerator streamingQuestionGenerator,
            CvRetrievalService cvRetrievalService,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.questionResponseStore = questionResponseStore;
        this.streamingQuestionGenerator = streamingQuestionGenerator;
        this.cvRetrievalService = cvRetrievalService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    void generate(AcceptedGeneration accepted) {
        try {
            InterviewSession session = sessionRepository.findById(accepted.sessionId())
                    .orElseThrow(() -> new SessionNotFoundException(accepted.sessionId()));

            if (session.state() instanceof SessionState.Cancelled) {
                cancelActiveResponse(accepted.responseId());
                return;
            }

            questionResponseStore.markStreaming(accepted.responseId());
            InterviewContext context = buildInterviewContext(session);

            String question = streamingQuestionGenerator.generateNextQuestion(
                    session.transcript(),
                    context,
                    partial -> persistPartialIfActive(accepted.responseId(), partial));

            finalizeSuccess(accepted.sessionId(), accepted.responseId(), question);
        } catch (QuestionGenerationException exception) {
            log.warn("Question generation failed for session {}", accepted.sessionId(), exception);
            finalizeFailure(accepted.responseId());
        }
    }

    private void persistPartialIfActive(ResponseId responseId, String partial) {
        QuestionResponse response = questionResponseStore.findById(responseId).orElseThrow();
        if (response.status() != QuestionResponseStatus.STREAMING) {
            return;
        }
        questionResponseStore.appendTokenEvent(responseId, partial);
    }

    private void finalizeSuccess(SessionId sessionId, ResponseId responseId, String question) {
        transactionTemplate.executeWithoutResult(status -> {
            QuestionResponse response = questionResponseStore.findById(responseId).orElseThrow();
            if (response.status().isTerminal()) {
                return;
            }

            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new SessionNotFoundException(sessionId));
            if (session.state() instanceof SessionState.Cancelled) {
                cancelActiveResponse(responseId);
                return;
            }

            InterviewSession updated = session.apply(new SessionCommand.AskQuestion(question, clock.instant()));
            sessionRepository.save(updated);
            questionResponseStore.markCompleted(responseId, question);
        });
    }

    private void finalizeFailure(ResponseId responseId) {
        transactionTemplate.executeWithoutResult(status -> {
            QuestionResponse response = questionResponseStore.findById(responseId).orElseThrow();
            if (response.status().isTerminal()) {
                return;
            }
            questionResponseStore.markFailed(responseId, SAFE_FAILURE_MESSAGE);
        });
    }

    private void cancelActiveResponse(ResponseId responseId) {
        try {
            QuestionResponse response = questionResponseStore.findById(responseId).orElseThrow();
            if (response.status().isActive()) {
                questionResponseStore.markCancelled(responseId);
            }
        } catch (InvalidQuestionResponseStatusTransitionException exception) {
            log.debug("Response {} was already terminal when cancellation was requested", responseId.value());
        }
    }

    private InterviewContext buildInterviewContext(InterviewSession session) {
        return session.cvId()
                .map(cvId -> toInterviewContext(cvId, session))
                .orElseGet(InterviewContext::empty);
    }

    private InterviewContext toInterviewContext(CvId cvId, InterviewSession session) {
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
}
