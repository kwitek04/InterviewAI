package com.interviewai.session.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data JPA repository over {@link InterviewSessionEntity}.
 */
interface InterviewSessionJpaRepository extends JpaRepository<InterviewSessionEntity, UUID> {
}
