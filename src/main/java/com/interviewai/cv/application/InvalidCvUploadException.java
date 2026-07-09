package com.interviewai.cv.application;

/**
 * Thrown when a CV upload request fails validation (not a PDF, empty, too
 * large, missing job offer, or no extractable text).
 */
public final class InvalidCvUploadException extends RuntimeException {

    public InvalidCvUploadException(String message) {
        super(message);
    }
}
