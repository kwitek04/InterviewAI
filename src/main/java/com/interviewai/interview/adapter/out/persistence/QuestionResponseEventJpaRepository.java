package com.interviewai.interview.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface QuestionResponseEventJpaRepository extends JpaRepository<QuestionResponseEventEntity, QuestionResponseEventId> {

    @Query("""
            SELECT event FROM QuestionResponseEventEntity event
            WHERE event.id.responseId = :responseId
              AND event.id.sequence > :afterSequence
            ORDER BY event.id.sequence ASC
            """)
    List<QuestionResponseEventEntity> findByResponseIdAndSequenceGreaterThan(
            @Param("responseId") UUID responseId,
            @Param("afterSequence") int afterSequence);
}
