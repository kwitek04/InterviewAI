package com.interviewai.session.adapter.out.messaging;

import com.interviewai.report.adapter.in.messaging.InterviewCompletedQueueConsumer;
import com.interviewai.session.application.InterviewCompletedSqsRelay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ApiRuntimeProfileIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("API runtime exposes web MVC and does not register the worker SQS consumer")
    void apiProfile_hasWebMvcAndNoWorkerConsumer() {
        assertThat(applicationContext.getBeansOfType(InterviewCompletedQueueConsumer.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(RequestMappingHandlerMapping.class)).isNotEmpty();
        assertThat(applicationContext.getBeansOfType(InterviewCompletedSqsRelay.class)).isNotEmpty();
    }
}
