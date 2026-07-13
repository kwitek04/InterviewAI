package com.interviewai.interview.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

interface QuestionResponseJpaRepository extends JpaRepository<QuestionResponseEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT response FROM QuestionResponseEntity response WHERE response.id = :id")
    Optional<QuestionResponseEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT response FROM QuestionResponseEntity response
            WHERE response.sessionId = :sessionId
              AND response.status IN ('PENDING', 'STREAMING')
            """)
    Optional<QuestionResponseEntity> findActiveBySessionId(@Param("sessionId") UUID sessionId);
}
