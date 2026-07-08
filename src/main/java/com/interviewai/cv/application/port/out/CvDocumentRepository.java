package com.interviewai.cv.application.port.out;

import com.interviewai.cv.domain.CvDocument;
import com.interviewai.shared.CvId;

import java.util.Optional;

/**
 * Persistence port for {@link CvDocument} aggregates.
 */
public interface CvDocumentRepository {

    void save(CvDocument document);

    Optional<CvDocument> findById(CvId id);
}
