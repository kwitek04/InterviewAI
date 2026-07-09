package com.interviewai.cv.application;

/**
 * Thrown when a {@link com.interviewai.cv.application.port.out.CvTextExtractor}
 * fails to extract text from an uploaded document.
 */
public final class CvParsingException extends RuntimeException {

    public CvParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
