package com.interviewai.interview.adapter.out.llm;

import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.interview.application.port.out.QuestionGenerator;
import com.interviewai.session.domain.Transcript;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

/**
 * {@link QuestionGenerator} backed by a LangChain4j {@link ChatModel}.
 */
@Component
class OllamaQuestionGenerator implements QuestionGenerator {

    private final ChatModel chatModel;
    private final InterviewChatMessageMapper messageMapper;

    OllamaQuestionGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.messageMapper = new InterviewChatMessageMapper();
    }

    @Override
    public String generateNextQuestion(Transcript transcript, InterviewContext context) {
        String raw = chatModel.chat(messageMapper.toChatMessages(transcript, context)).aiMessage().text();
        return sanitizeQuestion(raw);
    }

    static String sanitizeQuestion(String raw) {
        return QuestionTextSanitizer.sanitizeQuestion(raw);
    }
}
