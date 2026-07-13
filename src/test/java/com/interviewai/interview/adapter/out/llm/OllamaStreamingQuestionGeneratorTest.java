package com.interviewai.interview.adapter.out.llm;

import com.interviewai.interview.application.QuestionGenerationException;
import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.session.domain.Message;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.Transcript;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaStreamingQuestionGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private StreamingChatModel streamingChatModel;

    private OllamaStreamingQuestionGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new OllamaStreamingQuestionGenerator(streamingChatModel);
    }

    @Test
    @DisplayName("forwards partial responses in order and returns sanitized completion text")
    void generateNextQuestion_forwardsPartialsInOrderAndReturnsSanitizedCompletion() {
        List<String> partials = new ArrayList<>();
        stubStreaming(handler -> {
            handler.onPartialResponse("Tell me ");
            handler.onPartialResponse("about yourself.");
            handler.onCompleteResponse(response("Tell me about yourself."));
        });

        String question = generator.generateNextQuestion(
                Transcript.empty(),
                InterviewContext.empty(),
                partials::add);

        assertThat(question).isEqualTo("Tell me about yourself.");
        assertThat(partials).containsExactly("Tell me ", "about yourself.");
    }

    @Test
    @DisplayName("maps INTERVIEWER turns to AiMessage and CANDIDATE turns to UserMessage, in order")
    void generateNextQuestion_withTranscript_mapsRolesInOrder() {
        stubStreaming(handler -> handler.onCompleteResponse(response("What is your Spring Boot experience?")));
        Transcript transcript = Transcript.empty()
                .append(new Message(MessageRole.INTERVIEWER, "Tell me about yourself.", NOW))
                .append(new Message(MessageRole.CANDIDATE, "I am a backend engineer.", NOW));

        generator.generateNextQuestion(transcript, InterviewContext.empty(), partial -> {
        });

        List<ChatMessage> sent = captureSentMessages();
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(sent.get(1)).isInstanceOfSatisfying(AiMessage.class,
                aiMessage -> assertThat(aiMessage.text()).isEqualTo("Tell me about yourself."));
        assertThat(sent.get(2)).isInstanceOfSatisfying(UserMessage.class,
                userMessage -> assertThat(userMessage.singleText()).isEqualTo("I am a backend engineer."));
    }

    @Test
    @DisplayName("includes job offer and CV excerpts in the system prompt")
    void generateNextQuestion_withContext_embedsExcerptsInSystemPrompt() {
        stubStreaming(handler -> handler.onCompleteResponse(response("Can you tell me more about Allegro?")));

        generator.generateNextQuestion(
                Transcript.empty(),
                new InterviewContext("Senior Java role", List.of("Migrated Allegro services to AWS")),
                partial -> {
                });

        List<ChatMessage> sent = captureSentMessages();
        assertThat(sent.getFirst()).isInstanceOf(SystemMessage.class);
        String systemPrompt = ((SystemMessage) sent.getFirst()).text();
        assertThat(systemPrompt).contains("Senior Java role");
        assertThat(systemPrompt).contains("Migrated Allegro services to AWS");
    }

    @Test
    @DisplayName("removes assistant prefix split across partial callbacks before forwarding")
    void generateNextQuestion_stripsAssistantPrefixSplitAcrossPartials() {
        List<String> partials = new ArrayList<>();
        stubStreaming(handler -> {
            handler.onPartialResponse("assis");
            handler.onPartialResponse("tant");
            handler.onPartialResponse(": ");
            handler.onPartialResponse("How did you ");
            handler.onPartialResponse("use Java?");
            handler.onCompleteResponse(response("assistant: How did you use Java?"));
        });

        String question = generator.generateNextQuestion(Transcript.empty(), InterviewContext.empty(), partials::add);

        assertThat(question).isEqualTo("How did you use Java?");
        assertThat(partials).containsExactly("How did you ", "use Java?");
    }

    @Test
    @DisplayName("removes interviewer prefix split across partial callbacks before forwarding")
    void generateNextQuestion_stripsInterviewerPrefixSplitAcrossPartials() {
        List<String> partials = new ArrayList<>();
        stubStreaming(handler -> {
            handler.onPartialResponse("inter");
            handler.onPartialResponse("viewer");
            handler.onPartialResponse("\n\n");
            handler.onPartialResponse("Describe your ");
            handler.onPartialResponse("project.");
            handler.onCompleteResponse(response("interviewer\n\nDescribe your project."));
        });

        String question = generator.generateNextQuestion(Transcript.empty(), InterviewContext.empty(), partials::add);

        assertThat(question).isEqualTo("Describe your project.");
        assertThat(partials).containsExactly("Describe your ", "project.");
    }

    @Test
    @DisplayName("provider failure becomes QuestionGenerationException with cause preserved")
    void generateNextQuestion_onProviderError_throwsProjectExceptionWithCause() {
        RuntimeException providerFailure = new RuntimeException("ollama unavailable");
        stubStreaming(handler -> handler.onError(providerFailure));

        assertThatThrownBy(() -> generator.generateNextQuestion(
                Transcript.empty(),
                InterviewContext.empty(),
                partial -> {
                }))
                .isInstanceOf(QuestionGenerationException.class)
                .hasMessage("Question generation failed.")
                .hasCause(providerFailure);
    }

    @Test
    @DisplayName("blank provider completion is rejected")
    void generateNextQuestion_onBlankCompletion_throwsProjectException() {
        stubStreaming(handler -> handler.onCompleteResponse(response("   ")));

        assertThatThrownBy(() -> generator.generateNextQuestion(
                Transcript.empty(),
                InterviewContext.empty(),
                partial -> {
                }))
                .isInstanceOf(QuestionGenerationException.class)
                .hasMessage("Question generation returned blank output.");
    }

    @Test
    @DisplayName("null provider completion is rejected")
    void generateNextQuestion_onNullCompletion_throwsProjectException() {
        stubStreaming(handler -> handler.onCompleteResponse(nullTextResponse()));

        assertThatThrownBy(() -> generator.generateNextQuestion(
                Transcript.empty(),
                InterviewContext.empty(),
                partial -> {
                }))
                .isInstanceOf(QuestionGenerationException.class)
                .hasMessage("Question generation returned blank output.");
    }

    private void stubStreaming(java.util.function.Consumer<StreamingChatResponseHandler> interaction) {
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            interaction.accept(handler);
            return null;
        }).when(streamingChatModel).chat(anyList(), any(StreamingChatResponseHandler.class));
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    private ChatResponse nullTextResponse() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        AiMessage aiMessage = mock(AiMessage.class);
        when(chatResponse.aiMessage()).thenReturn(aiMessage);
        when(aiMessage.text()).thenReturn(null);
        return chatResponse;
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessage> captureSentMessages() {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(streamingChatModel).chat(captor.capture(), any(StreamingChatResponseHandler.class));
        return captor.getValue();
    }
}
