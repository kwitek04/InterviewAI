package com.interviewai.cv.application;

import com.interviewai.cv.domain.TextChunker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires stateless CV domain components into the Spring application context.
 */
@Configuration
class CvConfiguration {

    @Bean
    TextChunker textChunker() {
        return new TextChunker();
    }
}
