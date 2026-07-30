package com.interviewai.session.application;

import com.interviewai.shared.SessionId;

import java.util.List;
import java.util.Objects;

/**
 * Immutable read model of a completed interview used by report generation.
 */
public record CompletedInterviewSnapshot(SessionId sessionId, List<AnsweredQuestion> answeredQuestions) {

    public CompletedInterviewSnapshot {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        answeredQuestions = List.copyOf(Objects.requireNonNull(answeredQuestions, "answeredQuestions must not be null"));
    }

    /**
     * One answered interviewer question from the transcript, in conversation order.
     */
    public record AnsweredQuestion(int questionIndex, String question, String answer) {

        public AnsweredQuestion {
            if (questionIndex < 0) {
                throw new IllegalArgumentException("questionIndex must be >= 0");
            }
            Objects.requireNonNull(question, "question must not be null");
            if (question.isBlank()) {
                throw new IllegalArgumentException("question must not be blank");
            }
            Objects.requireNonNull(answer, "answer must not be null");
            if (answer.isBlank()) {
                throw new IllegalArgumentException("answer must not be blank");
            }
        }
    }
}
