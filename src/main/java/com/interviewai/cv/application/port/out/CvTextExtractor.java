package com.interviewai.cv.application.port.out;

/**
 * Extracts plain text from the raw bytes of an uploaded document.
 */
public interface CvTextExtractor {

    /**
     * @throws com.interviewai.cv.application.CvParsingException if the content
     *         cannot be parsed
     */
    String extractText(byte[] pdfContent);
}
