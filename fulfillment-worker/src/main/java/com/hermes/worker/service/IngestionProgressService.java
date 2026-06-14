package com.hermes.worker.service;

import com.hermes.common.domain.IngestionJob;
import com.hermes.common.repository.IngestionJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists ingestion progress in small, independently-committed transactions so
 * the running counter is visible to the SSE stream while a document is still
 * embedding — and so a worker that crashes mid-document can resume from where it
 * left off rather than starting over. Each method here is its own transaction;
 * the orchestrating {@link IngestionService} stays transaction-free and drives
 * the loop.
 */
@Service
public class IngestionProgressService {

    public enum Decision {
        /** Embed from {@code startIndex} onwards. */
        PROCEED,
        /** Already completed/failed — redelivery, do nothing. */
        DUPLICATE,
        /** No chunks to embed; the job has been marked FAILED. */
        EMPTY,
        /** The producing commit isn't visible yet; retry. */
        NOT_VISIBLE
    }

    public record Claim(Decision decision, int startIndex) {
    }

    private final IngestionJobRepository jobs;

    public IngestionProgressService(IngestionJobRepository jobs) {
        this.jobs = jobs;
    }

    /**
     * Atomically decides what to do with a job and, for a fresh one, moves it into
     * EMBEDDING so a concurrent redelivery can't double-start it. Resumes an
     * already-EMBEDDING job from its committed progress.
     */
    @Transactional
    public Claim claim(UUID jobId, int chunkCount) {
        IngestionJob job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            return new Claim(Decision.NOT_VISIBLE, 0);
        }
        return switch (job.getStatus()) {
            case COMPLETED, FAILED -> new Claim(Decision.DUPLICATE, 0);
            case EMBEDDING -> new Claim(Decision.PROCEED, job.getProcessedChunks());
            case PENDING -> {
                if (chunkCount <= 0) {
                    job.markFailed("EMPTY_DOCUMENT");
                    yield new Claim(Decision.EMPTY, 0);
                }
                job.markEmbedding();
                yield new Claim(Decision.PROCEED, 0);
            }
        };
    }

    /** Commit the running progress counter mid-document. */
    @Transactional
    public void advance(UUID jobId, int processedChunks) {
        jobs.findById(jobId).ifPresent(job -> job.recordProgress(processedChunks));
    }

    /** Mark the job COMPLETED with the final chunk count. */
    @Transactional
    public void complete(UUID jobId, int totalChunks) {
        jobs.findById(jobId).ifPresent(job -> {
            job.recordProgress(totalChunks);
            job.markCompleted();
        });
    }
}
