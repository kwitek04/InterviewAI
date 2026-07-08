package com.interviewai.cv.adapter.out.storage;

import com.interviewai.cv.application.FileNotFoundInStorageException;
import com.interviewai.cv.application.port.out.FileStorage;
import com.interviewai.cv.application.port.out.StoredFile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * {@link FileStorage} adapter backed by an S3-compatible object store.
 */
@Component
class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final S3StorageProperties properties;

    S3FileStorage(S3Client s3Client, S3StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public StoredFile store(String key, byte[] content, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
        return new StoredFile(key, content.length);
    }

    @Override
    public byte[] retrieve(String key) {
        try {
            return s3Client.getObjectAsBytes(
                            GetObjectRequest.builder().bucket(properties.bucket()).key(key).build())
                    .asByteArray();
        } catch (NoSuchKeyException exception) {
            throw new FileNotFoundInStorageException(key);
        }
    }
}
