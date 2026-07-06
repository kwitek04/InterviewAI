package com.interviewai.interview.adapter.out.llm;

import com.interviewai.interview.application.port.out.QuestionGenerator;
import com.interviewai.session.domain.Message;
import com.interviewai.session.domain.Transcript;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link QuestionGenerator} backed by a LangChain4j {@link ChatModel}.
 * <p>
 * Maps the domain {@link Transcript} onto the provider-agnostic LangChain4j
 * {@link ChatMessage} model before delegating to the underlying LLM.
 */
@Component
class OllamaQuestionGenerator implements QuestionGenerator {

    static final String SYSTEM_PROMPT = """
            You are an expert technical interviewer conducting a live interview with a candidate.
            Ask exactly one clear, focused question at a time based on the conversation so far.
            Do not answer on the candidate's behalf and do not repeat a question already asked.
            Respond with the question only, with no additional commentary or formatting.""";

    private final ChatModel chatModel;

    OllamaQuestionGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String generateNextQuestion(Transcript transcript) {
        return chatModel.chat(toChatMessages(transcript)).aiMessage().text();
    }

    private List<ChatMessage> toChatMessages(Transcript transcript) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        for (Message message : transcript.messages()) {
            messages.add(toChatMessage(message));
        }
        return messages;
    }

    private ChatMessage toChatMessage(Message message) {
        return switch (message.role()) {
            case INTERVIEWER -> AiMessage.from(message.content());
            case CANDIDATE -> UserMessage.from(message.content());
        };
    }
}
