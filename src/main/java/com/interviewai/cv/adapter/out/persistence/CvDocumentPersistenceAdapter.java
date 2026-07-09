package com.interviewai.cv.adapter.out.persistence;

import com.interviewai.cv.application.port.out.CvDocumentRepository;
import com.interviewai.cv.domain.CvDocument;
import com.interviewai.shared.CvId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Persistence adapter mapping the {@link CvDocument} aggregate to and from its
 * JPA representation.
 */
@Component
class CvDocumentPersistenceAdapter implements CvDocumentRepository {

    private final CvDocumentJpaRepository repository;

    CvDocumentPersistenceAdapter(CvDocumentJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(CvDocument document) {
        CvDocumentEntity entity = CvDocumentEntity.create(
                document.id().value(),
                document.fileName(),
                document.storageKey(),
                document.extractedText(),
                document.jobOffer(),
                document.uploadedAt());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CvDocument> findById(CvId id) {
        return repository.findById(id.value()).map(this::toDomain);
    }

    private CvDocument toDomain(CvDocumentEntity entity) {
        return new CvDocument(
                new CvId(entity.getId()),
                entity.getFileName(),
                entity.getStorageKey(),
                entity.getExtractedText(),
                entity.getJobOffer(),
                entity.getUploadedAt());
    }
}
