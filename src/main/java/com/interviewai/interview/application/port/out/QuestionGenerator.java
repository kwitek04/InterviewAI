package com.interviewai.interview.application.port.out;

import com.interviewai.session.domain.Transcript;

/**
 * Generates the interviewer's next question from the conversation so far.
 * <p>
 * Implemented by an adapter backed by an LLM; the application layer depends only
 * on this abstraction and never on a specific model provider.
 */
public interface QuestionGenerator {

    /**
     * Produces the next interview question given the transcript exchanged so far.
     */
    String generateNextQuestion(Transcript transcript, InterviewContext context);
}
