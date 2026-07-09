package com.interviewai.cv.application;

/**
 * Command for ingesting a CV document. When {@code plainText} is provided, PDF
 * validation and text extraction are skipped.
 */
public record CvUploadCommand(String fileName, byte[] pdfContent, String plainText, String jobOffer) {

    public static CvUploadCommand fromPdf(String fileName, byte[] pdfContent, String jobOffer) {
        return new CvUploadCommand(fileName, pdfContent, null, jobOffer);
    }

    public static CvUploadCommand fromPlainText(String fileName, String plainText, String jobOffer) {
        return new CvUploadCommand(fileName, null, plainText, jobOffer);
    }
}
