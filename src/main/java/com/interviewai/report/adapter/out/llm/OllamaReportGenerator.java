package com.interviewai.report.adapter.out.llm;

import com.interviewai.report.application.QuestionAssessmentValidator.ScoredAnswer;
import com.interviewai.report.application.ReportPromptAssembler;
import com.interviewai.report.application.SynthesisResult;
import com.interviewai.report.application.port.out.ReportGenerator;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

import java.util.List;

/**
 * LangChain4j/Ollama implementation of multi-stage structured report generation.
 */
class OllamaReportGenerator implements ReportGenerator {

    private final Stage1Assistant stage1Assistant;
    private final Stage2Assistant stage2Assistant;

    OllamaReportGenerator(ChatModel chatModel) {
        this(
                AiServices.builder(Stage1Assistant.class).chatModel(chatModel).build(),
                AiServices.builder(Stage2Assistant.class).chatModel(chatModel).build());
    }

    OllamaReportGenerator(Stage1Assistant stage1Assistant, Stage2Assistant stage2Assistant) {
        this.stage1Assistant = stage1Assistant;
        this.stage2Assistant = stage2Assistant;
    }

    @Override
    public List<ScoredAnswer> evaluateAnswers(CompletedInterviewSnapshot snapshot) {
        AssessmentBatchDto batch = stage1Assistant.evaluate(ReportPromptAssembler.stage1UserPrompt(snapshot));
        if (batch == null || batch.assessments() == null) {
            throw new IllegalStateException("Stage 1 returned no assessments");
        }
        return batch.assessments().stream()
                .map(item -> new ScoredAnswer(item.questionIndex(), item.score(), item.rationale()))
                .toList();
    }

    @Override
    public SynthesisResult synthesize(
            CompletedInterviewSnapshot snapshot, List<QuestionAssessment> assessments) {
        SynthesisDto synthesis = stage2Assistant.synthesize(
                ReportPromptAssembler.stage2UserPrompt(snapshot, assessments));
        if (synthesis == null) {
            throw new IllegalStateException("Stage 2 returned no synthesis");
        }
        return SynthesisResult.validated(
                synthesis.strengths(),
                synthesis.weaknesses(),
                synthesis.recommendations());
    }

    interface Stage1Assistant {

        @SystemMessage("""
                You are an interview evaluator. Return structured JSON only.
                Score each answered question from 1 to 5.
                Provide a concise rationale for each score.
                Keep questionIndex values aligned with the provided indexes.
                """)
        @UserMessage("{{payload}}")
        AssessmentBatchDto evaluate(@V("payload") String payload);
    }

    interface Stage2Assistant {

        @SystemMessage("""
                You are an interview coach. Return structured JSON only.
                Using the scored assessments, produce non-empty strengths, weaknesses,
                and recommendations. Keep entries concise and non-duplicated.
                """)
        @UserMessage("{{payload}}")
        SynthesisDto synthesize(@V("payload") String payload);
    }

    record AssessmentBatchDto(
            @Description("ordered per-question assessments") List<AssessmentItemDto> assessments) {
    }

    record AssessmentItemDto(
            @Description("zero-based question index") int questionIndex,
            @Description("score from 1 to 5") int score,
            @Description("short rationale for the score") String rationale) {
    }

    record SynthesisDto(
            @Description("candidate strengths") List<String> strengths,
            @Description("candidate weaknesses") List<String> weaknesses,
            @Description("actionable recommendations") List<String> recommendations) {
    }
}
