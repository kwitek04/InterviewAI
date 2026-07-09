package com.interviewai.interview.adapter.out.llm;

import com.interviewai.interview.application.InterviewPromptAssembler;
import com.interviewai.interview.application.port.out.InterviewContext;
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

    private final ChatModel chatModel;
    private final InterviewPromptAssembler promptAssembler;

    OllamaQuestionGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.promptAssembler = new InterviewPromptAssembler();
    }

    @Override
    public String generateNextQuestion(Transcript transcript, InterviewContext context) {
        String raw = chatModel.chat(toChatMessages(transcript, context)).aiMessage().text();
        return sanitizeQuestion(raw);
    }

    static String sanitizeQuestion(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.stripLeading()
                .replaceFirst("(?is)^(assistant|interviewer)\\s*:?\\s*(\\r?\\n)+", "")
                .replaceFirst("(?is)^(assistant|interviewer)\\s*:?\\s*", "")
                .strip();
    }

    private List<ChatMessage> toChatMessages(Transcript transcript, InterviewContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptAssembler.assemble(context)));
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
