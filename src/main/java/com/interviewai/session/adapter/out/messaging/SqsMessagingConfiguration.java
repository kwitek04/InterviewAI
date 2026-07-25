package com.interviewai.session.adapter.out.messaging;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Wires the AWS SDK {@link SqsClient} used to publish interview completion events.
 */
@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(SqsMessagingProperties.class)
class SqsMessagingConfiguration {

    @Bean
    SqsClient sqsClient(SqsMessagingProperties properties) {
        return SqsClient.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .build();
    }

    @Bean
    SqsQueueInitializer sqsQueueInitializer(SqsClient sqsClient, SqsMessagingProperties properties) {
        return new SqsQueueInitializer(sqsClient, properties);
    }
}
