package com.interviewai.support.report;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ControllableReportGeneratorConfiguration {

    @Bean
    @Primary
    ControllableReportGenerator controllableReportGenerator() {
        return new ControllableReportGenerator();
    }
}
