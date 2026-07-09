package com.interviewai.cv.adapter.out.persistence;

import com.interviewai.cv.application.port.out.CvChunkStore;
import com.interviewai.cv.application.port.out.EmbeddedChunk;
import com.interviewai.cv.application.port.out.ScoredChunk;
import com.interviewai.shared.CvId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({CvDocumentPersistenceAdapter.class, PgVectorCvChunkStore.class})
class PgVectorCvChunkStoreIT {

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
    private CvDocumentPersistenceAdapter cvDocumentPersistenceAdapter;

    @Autowired
    private CvChunkStore cvChunkStore;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("saved chunks can be queried by cosine similarity for one CV only")
    void saveAll_thenFindMostSimilar_returnsOrderedResultsForOneCvOnly() {
        CvId targetCvId = persistCvDocument("target-cv.pdf");
        CvId otherCvId = persistCvDocument("other-cv.pdf");
        entityManager.flush();

        cvChunkStore.saveAll(targetCvId, List.of(
                new EmbeddedChunk(0, "Java and Spring Boot", unitVector(0)),
                new EmbeddedChunk(1, "PostgreSQL and pgvector", unitVector(1)),
                new EmbeddedChunk(2, "Docker and LocalStack", unitVector(2))));
        cvChunkStore.saveAll(otherCvId, List.of(
                new EmbeddedChunk(0, "Other candidate chunk", unitVector(0))));
        flushAndClear();

        List<ScoredChunk> results = cvChunkStore.findMostSimilar(targetCvId, unitVector(0), 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).content()).isEqualTo("Java and Spring Boot");
        assertThat(results.get(0).score()).isCloseTo(1.0, within(1e-6));
        assertThat(results.get(1).content()).isIn("PostgreSQL and pgvector", "Docker and LocalStack");
        assertThat(results).extracting(ScoredChunk::content).doesNotContain("Other candidate chunk");
    }

    private float[] unitVector(int index) {
        float[] vector = new float[768];
        vector[index] = 1f;
        return vector;
    }

    private CvId persistCvDocument(String fileName) {
        CvId cvId = CvId.generate();
        cvDocumentPersistenceAdapter.save(new com.interviewai.cv.domain.CvDocument(
                cvId,
                fileName,
                "cv/" + cvId.value() + ".pdf",
                "Extracted text for " + fileName,
                "Backend engineer role",
                Instant.parse("2026-01-01T10:00:00Z")));
        return cvId;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
