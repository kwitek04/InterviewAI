package com.interviewai.cv.adapter.out.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Wires the AWS SDK {@link S3Client} used to store uploaded CVs, and ensures the
 * configured bucket exists on startup.
 */
@Configuration
@EnableConfigurationProperties(S3StorageProperties.class)
class S3StorageConfiguration {

    @Bean
    S3Client s3Client(S3StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(properties.pathStyleAccess())
                .build();
    }

    @Bean
    S3BucketInitializer s3BucketInitializer(S3Client s3Client, S3StorageProperties properties) {
        return new S3BucketInitializer(s3Client, properties.bucket());
    }
}
