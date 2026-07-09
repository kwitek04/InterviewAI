package com.interviewai.cv.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA representation of an uploaded CV document.
 */
@Entity
@Table(name = "cv_document")
class CvDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    private String extractedText;

    @Column(name = "job_offer", nullable = false, columnDefinition = "text")
    private String jobOffer;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected CvDocumentEntity() {
    }

    private CvDocumentEntity(
            UUID id, String fileName, String storageKey, String extractedText, String jobOffer, Instant uploadedAt) {
        this.id = id;
        this.fileName = fileName;
        this.storageKey = storageKey;
        this.extractedText = extractedText;
        this.jobOffer = jobOffer;
        this.uploadedAt = uploadedAt;
    }

    static CvDocumentEntity create(
            UUID id, String fileName, String storageKey, String extractedText, String jobOffer, Instant uploadedAt) {
        return new CvDocumentEntity(id, fileName, storageKey, extractedText, jobOffer, uploadedAt);
    }

    UUID getId() {
        return id;
    }

    String getFileName() {
        return fileName;
    }

    String getStorageKey() {
        return storageKey;
    }

    String getExtractedText() {
        return extractedText;
    }

    String getJobOffer() {
        return jobOffer;
    }

    Instant getUploadedAt() {
        return uploadedAt;
    }
}
