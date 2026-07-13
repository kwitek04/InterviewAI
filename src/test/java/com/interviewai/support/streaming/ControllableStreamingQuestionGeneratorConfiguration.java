package com.interviewai.support.streaming;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ControllableStreamingQuestionGeneratorConfiguration {

    @Bean
    @Primary
    ControllableStreamingQuestionGenerator controllableStreamingQuestionGenerator() {
        return new ControllableStreamingQuestionGenerator();
    }
}
