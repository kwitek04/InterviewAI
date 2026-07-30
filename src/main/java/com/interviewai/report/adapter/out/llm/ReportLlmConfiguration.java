package com.interviewai.report.adapter.out.llm;

import com.interviewai.report.application.ReportGenerationProperties;
import com.interviewai.report.application.port.out.ReportGenerator;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the LangChain4j chat model used for structured report generation.
 */
@Configuration
@EnableConfigurationProperties({ReportOllamaProperties.class, ReportGenerationProperties.class})
class ReportLlmConfiguration {

    @Bean
    ReportGenerator reportGenerator(ReportOllamaProperties properties) {
        ChatModel reportChatModel = OllamaChatModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(properties.modelName())
                .timeout(properties.timeout())
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .build();
        return new OllamaReportGenerator(reportChatModel);
    }
}
