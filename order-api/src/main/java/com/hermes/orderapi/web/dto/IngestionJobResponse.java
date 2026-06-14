package com.hermes.orderapi.web.dto;

import com.hermes.common.domain.IngestionJob;
import com.hermes.common.domain.IngestionStatus;

import java.time.Instant;
import java.util.UUID;

public record IngestionJobResponse(
        UUID jobId,
        String docId,
        String source,
        IngestionStatus status,
        int processedChunks,
        int totalChunks,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static IngestionJobResponse from(IngestionJob job) {
        return new IngestionJobResponse(
                job.getId(),
                job.getDocId(),
                job.getSource(),
                job.getStatus(),
                job.getProcessedChunks(),
                job.getTotalChunks(),
                job.getFailureReason(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
