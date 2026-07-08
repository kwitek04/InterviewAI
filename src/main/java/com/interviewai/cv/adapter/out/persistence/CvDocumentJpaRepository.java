package com.interviewai.cv.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository over {@link CvDocumentEntity}.
 */
interface CvDocumentJpaRepository extends JpaRepository<CvDocumentEntity, UUID> {
}
