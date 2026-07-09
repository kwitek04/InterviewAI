package com.interviewai.cv.application;

import com.interviewai.cv.domain.CvDocument;

/**
 * Outcome of a successful CV upload, including how many embedded chunks were
 * produced from the extracted text.
 */
public record CvUploadResult(CvDocument document, int chunkCount) {
}
