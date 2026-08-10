package com.interviewai.support.report;

import com.interviewai.report.application.QuestionAssessmentValidator.ScoredAnswer;
import com.interviewai.report.application.ReportGenerationException;
import com.interviewai.report.application.SynthesisResult;
import com.interviewai.report.application.port.out.ReportGenerator;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deterministic {@link ReportGenerator} for worker integration tests.
 */
public final class ControllableReportGenerator implements ReportGenerator {

    private final AtomicInteger evaluateInvocations = new AtomicInteger();
    private final AtomicInteger synthesizeInvocations = new AtomicInteger();
    private volatile CountDownLatch blockEvaluate = new CountDownLatch(0);
    private volatile boolean failEvaluate;
    private volatile boolean failSynthesize;
    private volatile long evaluateDelayMillis;

    public void blockEvaluate() {
        this.blockEvaluate = new CountDownLatch(1);
    }

    public void releaseEvaluate() {
        blockEvaluate.countDown();
    }

    public void failEvaluate() {
        this.failEvaluate = true;
    }

    public void failSynthesize() {
        this.failSynthesize = true;
    }

    public void succeed() {
        this.failEvaluate = false;
        this.failSynthesize = false;
        this.evaluateDelayMillis = 0L;
        this.blockEvaluate = new CountDownLatch(0);
    }

    public void delayEvaluate(long millis) {
        this.evaluateDelayMillis = millis;
    }

    public int evaluateInvocations() {
        return evaluateInvocations.get();
    }

    public int synthesizeInvocations() {
        return synthesizeInvocations.get();
    }

    public void resetInvocationCounts() {
        evaluateInvocations.set(0);
        synthesizeInvocations.set(0);
    }

    @Override
    public List<ScoredAnswer> evaluateAnswers(CompletedInterviewSnapshot snapshot) {
        evaluateInvocations.incrementAndGet();
        awaitBlock();
        sleep(evaluateDelayMillis);
        if (failEvaluate) {
            throw new ReportGenerationException("Controlled evaluate failure");
        }
        return snapshot.answeredQuestions().stream()
                .map(question -> new ScoredAnswer(question.questionIndex(), 4, "Solid"))
                .toList();
    }

    @Override
    public SynthesisResult synthesize(
            CompletedInterviewSnapshot snapshot, List<QuestionAssessment> assessments) {
        synthesizeInvocations.incrementAndGet();
        if (failSynthesize) {
            throw new ReportGenerationException("Controlled synthesize failure");
        }
        return SynthesisResult.validated(
                List.of("Clear communicator"),
                List.of("Needs more depth"),
                List.of("Prepare examples"));
    }

    private void awaitBlock() {
        try {
            if (!blockEvaluate.await(60, TimeUnit.SECONDS)) {
                throw new ReportGenerationException("Timed out waiting to release controlled evaluate");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ReportGenerationException("Interrupted while waiting to release controlled evaluate");
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ReportGenerationException("Interrupted during controlled evaluate delay");
        }
    }
}
