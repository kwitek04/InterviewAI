package com.interviewai.cv.adapter.in.web;

import com.interviewai.cv.domain.CvDocument;

import java.util.UUID;

/**
 * Response returned after a CV has been successfully uploaded and processed.
 */
record CvUploadResponse(UUID cvId, String fileName, int characterCount) {

    static CvUploadResponse from(CvDocument document) {
        return new CvUploadResponse(document.id().value(), document.fileName(), document.extractedText().length());
    }
}
