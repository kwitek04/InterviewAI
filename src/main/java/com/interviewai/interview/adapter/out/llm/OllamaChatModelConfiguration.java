package com.interviewai.interview.adapter.out.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the LangChain4j {@link ChatModel} used to talk to the local Ollama server.
 */
@Configuration
@EnableConfigurationProperties(OllamaProperties.class)
class OllamaChatModelConfiguration {

    @Bean
    ChatModel ollamaChatModel(OllamaProperties properties) {
        return OllamaChatModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .timeout(properties.timeout())
                .build();
    }

    @Bean
    StreamingChatModel ollamaStreamingChatModel(OllamaProperties properties) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .timeout(properties.timeout())
                .build();
    }
}
