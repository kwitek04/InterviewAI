package com.interviewai.report.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventEntity, UUID> {
}
