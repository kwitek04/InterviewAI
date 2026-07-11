package com.interviewai.interview.adapter.out.llm;

import com.interviewai.interview.application.QuestionGenerationException;
import com.interviewai.interview.application.port.out.InterviewContext;
import com.interviewai.interview.application.port.out.StreamingQuestionGenerator;
import com.interviewai.session.domain.Transcript;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * {@link StreamingQuestionGenerator} backed by a LangChain4j {@link StreamingChatModel}.
 */
@Component
class OllamaStreamingQuestionGenerator implements StreamingQuestionGenerator {

    private final StreamingChatModel streamingChatModel;
    private final InterviewChatMessageMapper messageMapper;

    OllamaStreamingQuestionGenerator(StreamingChatModel streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
        this.messageMapper = new InterviewChatMessageMapper();
    }

    @Override
    public String generateNextQuestion(
            Transcript transcript,
            InterviewContext context,
            Consumer<String> partialResponseConsumer) {
        List<ChatMessage> messages = messageMapper.toChatMessages(transcript, context);
        CompletableFuture<String> completion = new CompletableFuture<>();
        StreamingQuestionPrefixFilter prefixFilter = new StreamingQuestionPrefixFilter(partialResponseConsumer);

        streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                prefixFilter.accept(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                try {
                    String raw = completeResponse.aiMessage().text();
                    String sanitized = QuestionTextSanitizer.sanitizeQuestion(raw);
                    if (sanitized == null || sanitized.isBlank()) {
                        completion.completeExceptionally(
                                new QuestionGenerationException("Question generation returned blank output."));
                        return;
                    }

                    prefixFilter.finish();
                    if (!prefixFilter.emittedText().isEmpty()
                            && !prefixFilter.emittedText().equals(sanitized)) {
                        completion.completeExceptionally(new QuestionGenerationException(
                                "Streamed partial responses do not match the sanitized complete question."));
                        return;
                    }

                    completion.complete(sanitized);
                } catch (RuntimeException exception) {
                    completion.completeExceptionally(exception);
                }
            }

            @Override
            public void onError(Throwable error) {
                completion.completeExceptionally(
                        new QuestionGenerationException("Question generation failed.", error));
            }
        });

        try {
            return completion.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof QuestionGenerationException questionGenerationException) {
                throw questionGenerationException;
            }
            throw new QuestionGenerationException("Question generation failed.", cause);
        }
    }
}
