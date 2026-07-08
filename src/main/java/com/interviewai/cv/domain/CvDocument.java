package com.interviewai.cv.domain;

import com.interviewai.shared.CvId;

import java.time.Instant;
import java.util.Objects;

/**
 * An uploaded CV: its original file, where its bytes are stored, the text
 * extracted from it, and the job offer it was submitted against.
 */
public record CvDocument(
        CvId id, String fileName, String storageKey, String extractedText, String jobOffer, Instant uploadedAt) {

    public CvDocument {
        Objects.requireNonNull(id, "id must not be null");
        fileName = Preconditions.requireNonBlank(fileName, "fileName");
        storageKey = Preconditions.requireNonBlank(storageKey, "storageKey");
        extractedText = Preconditions.requireNonBlank(extractedText, "extractedText");
        jobOffer = Preconditions.requireNonBlank(jobOffer, "jobOffer");
        Objects.requireNonNull(uploadedAt, "uploadedAt must not be null");
    }
}
