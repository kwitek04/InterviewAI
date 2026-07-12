package com.interviewai.interview.adapter.in.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StreamingProperties.class)
class StreamingConfiguration {
}
