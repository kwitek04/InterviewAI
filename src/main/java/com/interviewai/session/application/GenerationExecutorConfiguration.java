package com.interviewai.session.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
class GenerationExecutorConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService questionGenerationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
