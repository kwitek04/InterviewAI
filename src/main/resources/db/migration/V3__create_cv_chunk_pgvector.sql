CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE cv_chunk (
    id          UUID PRIMARY KEY,
    cv_id       UUID NOT NULL REFERENCES cv_document (id),
    chunk_index INT  NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(768) NOT NULL,
    UNIQUE (cv_id, chunk_index)
);

CREATE INDEX cv_chunk_embedding_idx ON cv_chunk
    USING hnsw (embedding vector_cosine_ops);
