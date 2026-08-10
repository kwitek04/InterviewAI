package com.interviewai.report.adapter.in.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
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
@ActiveProfiles("worker")
@Testcontainers
class WorkerRuntimeProfileIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("interviewai.report.worker.enabled", () -> "false");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("worker runtime registers SQS consumer infrastructure and disables the web server")
    void workerProfile_hasConsumerAndNoWebServer() {
        assertThat(environment.getActiveProfiles()).contains("worker");
        assertThat(environment.getProperty("spring.main.web-application-type")).isEqualTo("none");
        assertThat(environment.getProperty("spring.main.keep-alive")).isEqualTo("true");
        assertThat(applicationContext.getBeansOfType(InterviewCompletedQueueConsumer.class)).hasSize(1);
        assertThat(applicationContext.getBean(InterviewCompletedQueueConsumer.class).isEnabled()).isFalse();
        assertThat(applicationContext.getBeansOfType(RequestMappingHandlerMapping.class)).isEmpty();
    }
}
