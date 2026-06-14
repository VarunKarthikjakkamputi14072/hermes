package com.hermes.orderapi.web.dto;

import com.hermes.common.domain.IngestionJob;
import com.hermes.common.domain.IngestionStatus;

import java.time.Instant;
import java.util.UUID;

/** Wire format pushed to the browser on every SSE frame of an ingestion job. */
public record IngestionProgress(
        long ts,
        UUID jobId,
        IngestionStatus status,
        int processedChunks,
        int totalChunks,
        int percent,
        String failureReason
) {
    public static IngestionProgress from(IngestionJob job) {
        int total = job.getTotalChunks();
        int processed = job.getProcessedChunks();
        int percent = total > 0
                ? (int) Math.min(100, Math.round(processed * 100.0 / total))
                : (job.getStatus() == IngestionStatus.COMPLETED ? 100 : 0);
        return new IngestionProgress(
                Instant.now().toEpochMilli(),
                job.getId(),
                job.getStatus(),
                processed,
                total,
                percent,
                job.getFailureReason()
        );
    }

    public boolean isTerminal() {
        return status == IngestionStatus.COMPLETED || status == IngestionStatus.FAILED;
    }
}
