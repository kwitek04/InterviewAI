package com.interviewai.cv.adapter.out.storage;

import com.interviewai.cv.application.FileNotFoundInStorageException;
import com.interviewai.cv.application.port.out.FileStorage;
import com.interviewai.cv.application.port.out.StoredFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class S3FileStorageIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Container
    static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4")).withServices("s3");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("interviewai.storage.s3.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("interviewai.storage.s3.region", LOCALSTACK::getRegion);
        registry.add("interviewai.storage.s3.access-key", LOCALSTACK::getAccessKey);
        registry.add("interviewai.storage.s3.secret-key", LOCALSTACK::getSecretKey);
    }

    @Autowired
    private FileStorage fileStorage;

    @Test
    @DisplayName("storing content and retrieving it by the same key returns identical bytes")
    void store_thenRetrieve_returnsIdenticalBytes() {
        String key = "cv/" + UUID.randomUUID() + ".pdf";
        byte[] content = "%PDF-1.4 fake cv content".getBytes(StandardCharsets.UTF_8);

        StoredFile stored = fileStorage.store(key, content, "application/pdf");

        assertThat(stored.key()).isEqualTo(key);
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(fileStorage.retrieve(key)).isEqualTo(content);
    }

    @Test
    @DisplayName("retrieving an unknown key throws FileNotFoundInStorageException")
    void retrieve_withUnknownKey_throwsFileNotFoundInStorageException() {
        String key = "cv/" + UUID.randomUUID() + ".pdf";

        assertThatThrownBy(() -> fileStorage.retrieve(key))
                .isInstanceOf(FileNotFoundInStorageException.class)
                .hasMessageContaining(key);
    }
}
