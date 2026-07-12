package com.interviewai.interview.adapter.in.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InterviewStreamWebConfiguration {

    @Bean
    QuestionResponseSseMapper questionResponseSseMapper() {
        return new QuestionResponseSseMapper();
    }
}
