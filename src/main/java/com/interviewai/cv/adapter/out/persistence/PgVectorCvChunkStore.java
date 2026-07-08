package com.interviewai.cv.adapter.out.persistence;

import com.interviewai.cv.application.port.out.CvChunkStore;
import com.interviewai.cv.application.port.out.EmbeddedChunk;
import com.interviewai.cv.application.port.out.ScoredChunk;
import com.interviewai.shared.CvId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC-backed pgvector store for embedded CV chunks.
 */
@Component
class PgVectorCvChunkStore implements CvChunkStore {

    private static final String INSERT_SQL = """
            INSERT INTO cv_chunk (id, cv_id, chunk_index, content, embedding)
            VALUES (?, ?, ?, ?, CAST(? AS vector))
            """;

    private static final String FIND_MOST_SIMILAR_SQL = """
            SELECT content, 1 - (embedding <=> CAST(? AS vector)) AS score
            FROM cv_chunk
            WHERE cv_id = ?
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
            """;

    private final JdbcClient jdbcClient;

    PgVectorCvChunkStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void saveAll(CvId cvId, List<EmbeddedChunk> chunks) {
        Objects.requireNonNull(cvId, "cvId must not be null");
        Objects.requireNonNull(chunks, "chunks must not be null");

        for (EmbeddedChunk chunk : chunks) {
            jdbcClient.sql(INSERT_SQL)
                    .params(UUID.randomUUID(), cvId.value(), chunk.index(), chunk.content(), toPgVectorLiteral(chunk.embedding()))
                    .update();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScoredChunk> findMostSimilar(CvId cvId, float[] queryEmbedding, int limit) {
        Objects.requireNonNull(cvId, "cvId must not be null");
        Objects.requireNonNull(queryEmbedding, "queryEmbedding must not be null");
        if (queryEmbedding.length == 0) {
            throw new IllegalArgumentException("queryEmbedding must not be empty");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }

        String vectorLiteral = toPgVectorLiteral(queryEmbedding);
        return jdbcClient.sql(FIND_MOST_SIMILAR_SQL)
                .params(vectorLiteral, cvId.value(), vectorLiteral, limit)
                .query((resultSet, rowNum) -> new ScoredChunk(
                        resultSet.getString("content"),
                        resultSet.getDouble("score")))
                .list();
    }

    static String toPgVectorLiteral(float[] embedding) {
        Objects.requireNonNull(embedding, "embedding must not be null");
        if (embedding.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }

        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(",");
            }
            literal.append(toPlainNumber(embedding[index]));
        }
        return literal.append("]").toString();
    }

    private static String toPlainNumber(float value) {
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }
}
