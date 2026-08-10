package com.interviewai.report.application.port.out;

import com.interviewai.report.application.QuestionAssessmentValidator.ScoredAnswer;
import com.interviewai.report.application.SynthesisResult;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;

import java.util.List;

/**
 * Provider-neutral multi-stage report generation port.
 */
public interface ReportGenerator {

    /**
     * Stage 1: score every answered question. Transcript text is bound by the application layer.
     */
    List<ScoredAnswer> evaluateAnswers(CompletedInterviewSnapshot snapshot);

    /**
     * Stage 2: synthesize strengths, weaknesses, and recommendations from validated assessments.
     */
    SynthesisResult synthesize(
            CompletedInterviewSnapshot snapshot, List<QuestionAssessment> assessments);
}
