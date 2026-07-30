package com.interviewai.report.application;

import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;

import java.util.List;
import java.util.StringJoiner;

/**
 * Builds provider-agnostic prompts for report generation stages.
 */
public final class ReportPromptAssembler {

    private ReportPromptAssembler() {
    }

    public static String stage1SystemPrompt() {
        return """
                You are an interview evaluator. Return structured JSON only.
                Score each answered question from 1 to 5.
                Keep the original question and answer text unchanged.
                Provide a concise rationale for each score.
                """.stripIndent().trim();
    }

    public static String stage1UserPrompt(CompletedInterviewSnapshot snapshot) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("Evaluate these answered interview questions:");
        for (CompletedInterviewSnapshot.AnsweredQuestion pair : snapshot.answeredQuestions()) {
            joiner.add("Index: " + pair.questionIndex());
            joiner.add("Question: " + pair.question());
            joiner.add("Answer: " + pair.answer());
            joiner.add("---");
        }
        return joiner.toString();
    }

    public static String stage2SystemPrompt() {
        return """
                You are an interview coach. Return structured JSON only.
                Using the scored assessments, produce non-empty strengths, weaknesses,
                and recommendations. Keep entries concise and non-duplicated.
                """.stripIndent().trim();
    }

    public static String stage2UserPrompt(
            CompletedInterviewSnapshot snapshot, List<QuestionAssessment> assessments) {
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add("Session: " + snapshot.sessionId().value());
        joiner.add("Assessments:");
        for (QuestionAssessment assessment : assessments) {
            joiner.add("Index: " + assessment.questionIndex());
            joiner.add("Question: " + assessment.question());
            joiner.add("Answer: " + assessment.answer());
            joiner.add("Score: " + assessment.score());
            joiner.add("Rationale: " + assessment.rationale());
            joiner.add("---");
        }
        return joiner.toString();
    }
}
