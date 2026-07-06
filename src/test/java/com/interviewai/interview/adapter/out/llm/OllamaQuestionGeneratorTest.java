package com.interviewai.interview.adapter.out.llm;

import com.interviewai.session.domain.Message;
import com.interviewai.session.domain.MessageRole;
import com.interviewai.session.domain.Transcript;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OllamaQuestionGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private ChatModel chatModel;

    private OllamaQuestionGenerator generator;

    @Test
    @DisplayName("sends only the system prompt when the transcript is empty")
    void generateNextQuestion_withEmptyTranscript_sendsOnlySystemPrompt() {
        generator = new OllamaQuestionGenerator(chatModel);
        when(chatModel.chat(anyList())).thenReturn(response("Tell me about yourself."));

        String question = generator.generateNextQuestion(Transcript.empty());

        assertThat(question).isEqualTo("Tell me about yourself.");
        List<ChatMessage> sent = captureSentMessages();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) sent.get(0)).text()).isEqualTo(OllamaQuestionGenerator.SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("maps INTERVIEWER turns to AiMessage and CANDIDATE turns to UserMessage, in order")
    void generateNextQuestion_withTranscript_mapsRolesInOrder() {
        generator = new OllamaQuestionGenerator(chatModel);
        when(chatModel.chat(anyList())).thenReturn(response("What is your experience with Spring Boot?"));
        Transcript transcript = Transcript.empty()
                .append(new Message(MessageRole.INTERVIEWER, "Tell me about yourself.", NOW))
                .append(new Message(MessageRole.CANDIDATE, "I am a backend engineer.", NOW));

        String question = generator.generateNextQuestion(transcript);

        assertThat(question).isEqualTo("What is your experience with Spring Boot?");
        List<ChatMessage> sent = captureSentMessages();
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(sent.get(1)).isInstanceOfSatisfying(AiMessage.class,
                aiMessage -> assertThat(aiMessage.text()).isEqualTo("Tell me about yourself."));
        assertThat(sent.get(2)).isInstanceOfSatisfying(UserMessage.class,
                userMessage -> assertThat(userMessage.singleText()).isEqualTo("I am a backend engineer."));
    }

    private ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    @SuppressWarnings("unchecked")
    private List<ChatMessage> captureSentMessages() {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatModel).chat(captor.capture());
        return captor.getValue();
    }
}
