package com.interviewai.cv.application;

import com.interviewai.cv.application.port.out.CvDocumentRepository;
import com.interviewai.cv.application.port.out.CvTextExtractor;
import com.interviewai.cv.application.port.out.FileStorage;
import com.interviewai.cv.domain.CvDocument;
import com.interviewai.shared.CvId;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Orchestrates the CV upload use case: validating the incoming file, storing
 * its bytes, extracting its text, and persisting the resulting document.
 * <p>
 * Coordinates the {@link FileStorage}, {@link CvTextExtractor}, and
 * {@link CvDocumentRepository} ports; it holds no parsing or storage logic
 * of its own.
 */
@Service
public class CvApplicationService {

    private static final byte[] PDF_MAGIC_BYTES = "%PDF".getBytes(StandardCharsets.US_ASCII);
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final FileStorage fileStorage;
    private final CvTextExtractor cvTextExtractor;
    private final CvDocumentRepository cvDocumentRepository;
    private final Clock clock;

    public CvApplicationService(
            FileStorage fileStorage,
            CvTextExtractor cvTextExtractor,
            CvDocumentRepository cvDocumentRepository,
            Clock clock) {
        this.fileStorage = fileStorage;
        this.cvTextExtractor = cvTextExtractor;
        this.cvDocumentRepository = cvDocumentRepository;
        this.clock = clock;
    }

    /**
     * Validates and stores the uploaded CV, extracts its text, and persists the
     * resulting {@link CvDocument}.
     *
     * @throws InvalidCvUploadException if the upload fails validation
     * @throws CvParsingException       if the document's text cannot be extracted
     */
    public CvDocument uploadCv(String fileName, byte[] pdfContent, String jobOffer) {
        validate(fileName, pdfContent, jobOffer);

        CvId cvId = CvId.generate();
        String storageKey = "cv/" + cvId.value() + ".pdf";
        fileStorage.store(storageKey, pdfContent, "application/pdf");

        String extractedText = cvTextExtractor.extractText(pdfContent);
        if (extractedText.isBlank()) {
            throw new InvalidCvUploadException("No text could be extracted from the uploaded document");
        }

        CvDocument document = new CvDocument(cvId, fileName, storageKey, extractedText, jobOffer, clock.instant());
        cvDocumentRepository.save(document);
        return document;
    }

    private void validate(String fileName, byte[] pdfContent, String jobOffer) {
        if (fileName == null || fileName.isBlank()) {
            throw new InvalidCvUploadException("fileName must not be blank");
        }
        if (pdfContent == null || pdfContent.length == 0) {
            throw new InvalidCvUploadException("The uploaded file must not be empty");
        }
        if (pdfContent.length > MAX_FILE_SIZE_BYTES) {
            throw new InvalidCvUploadException("The uploaded file must not exceed 5 MB");
        }
        if (!hasPdfMagicBytes(pdfContent)) {
            throw new InvalidCvUploadException("The uploaded file is not a PDF document");
        }
        if (jobOffer == null || jobOffer.isBlank()) {
            throw new InvalidCvUploadException("jobOffer must not be blank");
        }
    }

    private boolean hasPdfMagicBytes(byte[] content) {
        if (content.length < PDF_MAGIC_BYTES.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (content[i] != PDF_MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }
}
