package com.interviewai.interview.adapter.out.llm;

import com.interviewai.interview.application.InterviewPromptAssembler;
import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.session.domain.Message;
import com.interviewai.session.domain.Transcript;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;

final class InterviewChatMessageMapper {

    private final InterviewPromptAssembler promptAssembler;

    InterviewChatMessageMapper() {
        this.promptAssembler = new InterviewPromptAssembler();
    }

    List<ChatMessage> toChatMessages(Transcript transcript, InterviewContext context) {
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
