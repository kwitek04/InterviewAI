package com.interviewai.support.streaming;

import com.interviewai.interview.application.QuestionGenerationException;
import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.interview.application.port.out.StreamingQuestionGenerator;
import com.interviewai.session.domain.Transcript;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Deterministic {@link StreamingQuestionGenerator} for integration tests.
 */
public final class ControllableStreamingQuestionGenerator implements StreamingQuestionGenerator {

    private final AtomicReference<List<String>> tokens = new AtomicReference<>(List.of());
    private final AtomicReference<String> finalQuestion = new AtomicReference<>("");
    private volatile int pauseAfterTokenCount = -1;
    private volatile boolean failNext;
    private volatile CountDownLatch releaseLatch = new CountDownLatch(0);

    public void configure(List<String> partialTokens, String finalQuestion, int pauseAfterTokenCount) {
        this.tokens.set(List.copyOf(partialTokens));
        this.finalQuestion.set(finalQuestion);
        this.pauseAfterTokenCount = pauseAfterTokenCount;
        this.failNext = false;
        this.releaseLatch = pauseAfterTokenCount > 0 ? new CountDownLatch(1) : new CountDownLatch(0);
    }

    public void releasePausedGeneration() {
        releaseLatch.countDown();
    }

    public void failNextGeneration() {
        this.failNext = true;
    }

    @Override
    public String generateNextQuestion(
            Transcript transcript,
            InterviewContext context,
            Consumer<String> partialResponseConsumer) {
        if (failNext) {
            throw new QuestionGenerationException("Question generation failed.");
        }

        List<String> partials = tokens.get();
        for (int index = 0; index < partials.size(); index++) {
            partialResponseConsumer.accept(partials.get(index));
            if (index + 1 == pauseAfterTokenCount) {
                awaitRelease();
            }
        }
        return finalQuestion.get();
    }

    private void awaitRelease() {
        try {
            if (!releaseLatch.await(30, TimeUnit.SECONDS)) {
                throw new QuestionGenerationException("Timed out waiting to release controlled generation.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new QuestionGenerationException("Interrupted while waiting to release controlled generation.");
        }
    }
}
