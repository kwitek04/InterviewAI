package com.interviewai.report.adapter.out.llm;

import com.interviewai.report.application.QuestionAssessmentValidator.ScoredAnswer;
import com.interviewai.report.application.ReportPromptAssembler;
import com.interviewai.report.application.SynthesisResult;
import com.interviewai.report.domain.QuestionAssessment;
import com.interviewai.session.application.CompletedInterviewSnapshot;
import com.interviewai.shared.SessionId;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
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
    @DisplayName("stage 1 requests a JSON schema response and maps it to scored answers")
    void evaluateAnswers_requestsJsonSchemaAndMapsResponse() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"assessments":[
                  {"questionIndex":0,"score":4,"rationale":"Solid"},
                  {"questionIndex":1,"score":2,"rationale":"Thin"}
                ]}
                """);

        List<ScoredAnswer> scored = new OllamaReportGenerator(chatModel).evaluateAnswers(snapshot());

        assertThat(scored).containsExactly(
                new ScoredAnswer(0, 4, "Solid"),
                new ScoredAnswer(1, 2, "Thin"));

        assertStructuredJsonRequest(chatModel.singleRequest(), ReportPromptAssembler.stage1SystemPrompt());
    }

    @Test
    @DisplayName("stage 2 requests a JSON schema response and maps it to a synthesis")
    void synthesize_requestsJsonSchemaAndMapsResponse() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {"strengths":["Clear communicator"],
                 "weaknesses":["Needs depth"],
                 "recommendations":["Prepare examples"]}
                """);
        List<QuestionAssessment> assessments = List.of(
                new QuestionAssessment(0, "Q0", "A0", 4, "Solid"),
                new QuestionAssessment(1, "Q1", "A1", 2, "Thin"));

        SynthesisResult result = new OllamaReportGenerator(chatModel).synthesize(snapshot(), assessments);

        assertThat(result.strengths()).containsExactly("Clear communicator");
        assertThat(result.weaknesses()).containsExactly("Needs depth");
        assertThat(result.recommendations()).containsExactly("Prepare examples");

        assertStructuredJsonRequest(chatModel.singleRequest(), ReportPromptAssembler.stage2SystemPrompt());
    }

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
    @DisplayName("null stage-1 batch fails without invoking stage 2")
    void evaluateAnswers_whenNullBatch_throws() {
        when(stage1Assistant.evaluate(anyString())).thenReturn(null);
        OllamaReportGenerator generator = new OllamaReportGenerator(stage1Assistant, stage2Assistant);

        assertThatThrownBy(() -> generator.evaluateAnswers(snapshot()))
                .isInstanceOf(IllegalStateException.class);
        verify(stage2Assistant, never()).synthesize(anyString());
    }

    @Test
    @DisplayName("null stage-2 synthesis is rejected")
    void synthesize_whenNullSynthesis_throws() {
        when(stage2Assistant.synthesize(anyString())).thenReturn(null);
        OllamaReportGenerator generator = new OllamaReportGenerator(stage1Assistant, stage2Assistant);

        assertThatThrownBy(() -> generator.synthesize(
                snapshot(), List.of(new QuestionAssessment(0, "Q0", "A0", 4, "Solid"))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static void assertStructuredJsonRequest(ChatRequest request, String expectedSystemPrompt) {
        assertThat(request.responseFormat().type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(request.responseFormat().jsonSchema()).isNotNull();
        assertThat(request.messages().getFirst()).isInstanceOfSatisfying(
                SystemMessage.class,
                message -> assertThat(message.text()).isEqualTo(expectedSystemPrompt));
    }

    private static CompletedInterviewSnapshot snapshot() {
        return new CompletedInterviewSnapshot(
                SessionId.generate(),
                List.of(
                        new CompletedInterviewSnapshot.AnsweredQuestion(0, "Q0", "A0"),
                        new CompletedInterviewSnapshot.AnsweredQuestion(1, "Q1", "A1")));
    }

    /**
     * Chat model that replays scripted JSON and records the requests LangChain4j built,
     * so the adapter can be tested without a running Ollama instance.
     */
    private static final class RecordingChatModel implements ChatModel {

        private final Deque<String> scriptedResponses;
        private final List<ChatRequest> requests = new ArrayList<>();

        private RecordingChatModel(String... jsonResponses) {
            this.scriptedResponses = new ArrayDeque<>(List.of(jsonResponses));
        }

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            requests.add(chatRequest);
            String json = scriptedResponses.poll();
            if (json == null) {
                throw new IllegalStateException("No scripted response left for " + chatRequest);
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        }

        private ChatRequest singleRequest() {
            assertThat(requests).hasSize(1);
            return requests.getFirst();
        }
    }
}
