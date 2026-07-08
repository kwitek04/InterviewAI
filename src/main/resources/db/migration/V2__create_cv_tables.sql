CREATE TABLE cv_document (
    id             UUID PRIMARY KEY,
    file_name      VARCHAR(255)  NOT NULL,
    storage_key    VARCHAR(512)  NOT NULL,
    extracted_text TEXT          NOT NULL,
    job_offer      TEXT          NOT NULL,
    uploaded_at    TIMESTAMPTZ   NOT NULL
);
