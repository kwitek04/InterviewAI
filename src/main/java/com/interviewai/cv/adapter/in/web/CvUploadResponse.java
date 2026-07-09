package com.interviewai.cv.adapter.in.web;

import com.interviewai.cv.application.CvUploadResult;

import java.util.UUID;

/**
 * Response returned after a CV has been successfully uploaded and processed.
 */
record CvUploadResponse(UUID cvId, String fileName, int characterCount, int chunkCount) {

    static CvUploadResponse from(CvUploadResult result) {
        return new CvUploadResponse(
                result.document().id().value(),
                result.document().fileName(),
                result.document().extractedText().length(),
                result.chunkCount());
    }
}
