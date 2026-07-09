ALTER TABLE interview_session
    ADD COLUMN cv_id UUID NULL REFERENCES cv_document (id);
