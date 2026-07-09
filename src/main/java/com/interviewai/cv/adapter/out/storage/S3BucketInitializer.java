package com.interviewai.cv.adapter.out.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Ensures the configured CV storage bucket exists once the application context has
 * finished starting up, creating it if necessary.
 * <p>
 * The object store is treated as an optional dependency at startup: if it cannot be
 * reached, the failure is logged rather than propagated, so that the application
 * still starts and only CV storage operations are affected.
 */
class S3BucketInitializer implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(S3BucketInitializer.class);

    private final S3Client s3Client;
    private final String bucket;

    S3BucketInitializer(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            ensureBucketExists();
        } catch (SdkException exception) {
            log.warn("Could not verify or create the CV storage bucket '{}'; "
                    + "CV storage operations will fail until the object store is reachable.", bucket, exception);
        }
    }

    private void ensureBucketExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException exception) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
}
