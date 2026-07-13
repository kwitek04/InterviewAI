package com.interviewai.interview.application.port.out;

import com.interviewai.session.domain.Transcript;

import java.util.function.Consumer;

/**
 * Generates the interviewer's next question while streaming provider partial responses.
 */
public interface StreamingQuestionGenerator {

    /**
     * Streams partial responses to the consumer and returns the authoritative complete question
     * after successful provider completion.
     */
    String generateNextQuestion(
            Transcript transcript,
            InterviewContext context,
            Consumer<String> partialResponseConsumer);
}
