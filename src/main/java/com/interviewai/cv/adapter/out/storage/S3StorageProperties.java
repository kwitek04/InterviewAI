package com.interviewai.cv.adapter.out.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Connection settings for the S3-compatible object store, bound from
 * {@code interviewai.storage.s3.*} configuration properties.
 */
@ConfigurationProperties(prefix = "interviewai.storage.s3")
record S3StorageProperties(
        URI endpoint, String region, String bucket, String accessKey, String secretKey, boolean pathStyleAccess) {
}
