package com.interviewai.report.adapter.out.persistence;

import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.report.domain.ReportStatus;

import java.time.Instant;
import java.util.List;

record ReportPayloadJson(
        List<QuestionAssessmentJson> questionAssessments,
        List<String> strengths,
        List<String> weaknesses,
        List<String> recommendations,
        Instant generatedAt) {

    record QuestionAssessmentJson(
            int questionIndex,
            String question,
            String answer,
            int score,
            String rationale) {

        static QuestionAssessmentJson from(QuestionAssessment assessment) {
            return new QuestionAssessmentJson(
                    assessment.questionIndex(),
                    assessment.question(),
                    assessment.answer(),
                    assessment.score(),
                    assessment.rationale());
        }

        QuestionAssessment toDomain() {
            return new QuestionAssessment(questionIndex, question, answer, score, rationale);
        }
    }

    static ReportPayloadJson fromReady(
            List<QuestionAssessment> assessments,
            List<String> strengths,
            List<String> weaknesses,
            List<String> recommendations,
            Instant generatedAt) {
        return new ReportPayloadJson(
                assessments.stream().map(QuestionAssessmentJson::from).toList(),
                strengths,
                weaknesses,
                recommendations,
                generatedAt);
    }
}
