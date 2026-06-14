package com.hermes.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the progress of asynchronously embedding one document. The API creates
 * it PENDING and hands the work to Kafka; an ingestion worker drives it through
 * EMBEDDING to COMPLETED. {@code processedChunks}/{@code totalChunks} is what the
 * UI streams as a progress bar — the same idea as the order console, applied to
 * document ingestion instead of fulfilment.
 */
@Entity
@Table(name = "ingestion_jobs", indexes = {
        @Index(name = "idx_ingestion_status", columnList = "status"),
        @Index(name = "idx_ingestion_created_at", columnList = "created_at")
})
public class IngestionJob {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "doc_id", nullable = false)
    private String docId;

    @Column(name = "source")
    private String source;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "processed_chunks", nullable = false)
    private int processedChunks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IngestionStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IngestionJob() {
        // for JPA
    }

    public IngestionJob(UUID id, String docId, String source, int totalChunks) {
        this.id = id;
        this.docId = docId;
        this.source = source;
        this.totalChunks = totalChunks;
        this.processedChunks = 0;
        this.status = IngestionStatus.PENDING;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markEmbedding() {
        this.status = IngestionStatus.EMBEDDING;
        this.failureReason = null;
    }

    /** One chunk embedded and written; advances the progress counter. */
    public void recordChunkEmbedded() {
        this.processedChunks++;
    }

    public void markCompleted() {
        this.status = IngestionStatus.COMPLETED;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = IngestionStatus.FAILED;
        this.failureReason = reason;
    }

    public boolean isPending() {
        return status == IngestionStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public String getDocId() {
        return docId;
    }

    public String getSource() {
        return source;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public int getProcessedChunks() {
        return processedChunks;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
