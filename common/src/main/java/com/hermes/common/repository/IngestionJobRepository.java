package com.hermes.common.repository;

import com.hermes.common.domain.IngestionJob;
import com.hermes.common.domain.IngestionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    long countByStatus(IngestionStatus status);
}
