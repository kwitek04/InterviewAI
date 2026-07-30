package com.interviewai.report.adapter.out.llm;

import com.interviewai.report.application.QuestionAssessmentValidator.ScoredAnswer;
import com.interviewai.report.application.SynthesisResult;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;
import com.interviewai.shared.SessionId;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaReportGeneratorTest {

    @Mock
    private OllamaReportGenerator.Stage1Assistant stage1Assistant;

    @Mock
    private OllamaReportGenerator.Stage2Assistant stage2Assistant;

    @Test
    @DisplayName("evaluateAnswers maps structured stage-1 output without calling Ollama")
    void evaluateAnswers_mapsStructuredOutput() {
        CompletedInterviewSnapshot snapshot = snapshot();
        when(stage1Assistant.evaluate(anyString())).thenReturn(new OllamaReportGenerator.AssessmentBatchDto(List.of(
                new OllamaReportGenerator.AssessmentItemDto(0, 4, "Solid"),
                new OllamaReportGenerator.AssessmentItemDto(1, 2, "Thin"))));

        OllamaReportGenerator generator = new OllamaReportGenerator(stage1Assistant, stage2Assistant);
        List<ScoredAnswer> scored = generator.evaluateAnswers(snapshot);

        assertThat(scored).containsExactly(
                new ScoredAnswer(0, 4, "Solid"),
                new ScoredAnswer(1, 2, "Thin"));
        verify(stage2Assistant, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("synthesize maps structured stage-2 output")
    void synthesize_mapsStructuredOutput() {
        CompletedInterviewSnapshot snapshot = snapshot();
        List<QuestionAssessment> assessments = List.of(
                new QuestionAssessment(0, "Q0", "A0", 4, "Solid"),
                new QuestionAssessment(1, "Q1", "A1", 2, "Thin"));
        when(stage2Assistant.synthesize(anyString())).thenReturn(new OllamaReportGenerator.SynthesisDto(
                List.of("Clear communicator"),
                List.of("Needs depth"),
                List.of("Prepare examples")));

        OllamaReportGenerator generator = new OllamaReportGenerator(stage1Assistant, stage2Assistant);
        SynthesisResult result = generator.synthesize(snapshot, assessments);

        assertThat(result.strengths()).containsExactly("Clear communicator");
        assertThat(result.weaknesses()).containsExactly("Needs depth");
        assertThat(result.recommendations()).containsExactly("Prepare examples");
    }

    @Test
    @DisplayName("null stage-1 batch fails without invoking stage 2")
    void evaluateAnswers_whenNullBatch_throws() {
        when(stage1Assistant.evaluate(anyString())).thenReturn(null);
        OllamaReportGenerator generator = new OllamaReportGenerator(stage1Assistant, stage2Assistant);

        assertThatThrownBy(() -> generator.evaluateAnswers(snapshot()))
                .isInstanceOf(IllegalStateException.class);
        verify(stage2Assistant, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("ChatModel-backed constructor builds without contacting a real Ollama endpoint")
    void chatModelConstructor_doesNotCallModelUntilUsed() {
        ChatModel unusedModel = mock(ChatModel.class);
        OllamaReportGenerator generator = new OllamaReportGenerator(unusedModel);
        assertThat(generator).isNotNull();
        Mockito.verifyNoInteractions(unusedModel);
    }

    private static CompletedInterviewSnapshot snapshot() {
        return new CompletedInterviewSnapshot(
                SessionId.generate(),
                List.of(
                        new CompletedInterviewSnapshot.AnsweredQuestion(0, "Q0", "A0"),
                        new CompletedInterviewSnapshot.AnsweredQuestion(1, "Q1", "A1")));
    }
}
