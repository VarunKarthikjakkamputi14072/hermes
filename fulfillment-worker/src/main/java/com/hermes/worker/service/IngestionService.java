package com.hermes.worker.service;

import com.hermes.common.domain.IngestionJob;
import com.hermes.common.event.IngestRequestedEvent;
import com.hermes.common.repository.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional heart of the ingestion path — the analogue of
 * {@code FulfillmentService}. Each call runs in a single DB transaction: the job
 * row is loaded, every chunk is embedded and written, and the job status is
 * advanced atomically. If any chunk fails, the whole unit of work rolls back and
 * Kafka redelivers the message; after the configured retries it lands on the
 * ingestion dead-letter topic.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionJobRepository jobRepository;
    private final Embedder embedder;

    public IngestionService(IngestionJobRepository jobRepository, Embedder embedder) {
        this.jobRepository = jobRepository;
        this.embedder = embedder;
    }

    @Transactional
    public IngestionResult ingest(IngestRequestedEvent event) {
        IngestionJob job = jobRepository.findById(event.jobId()).orElse(null);
        if (job == null) {
            // The API publishes inside its own DB transaction, so a fast worker can
            // consume the event before that commit is visible (a dual-write race).
            // Retrying after a back-off lets the commit land; if it never appears,
            // the message lands on the DLT.
            throw new JobNotYetVisibleException(event.jobId());
        }

        // Idempotency: redelivery of an already-processed job is a no-op. This
        // makes at-least-once delivery safe.
        if (!job.isPending()) {
            return IngestionResult.SKIPPED_DUPLICATE;
        }

        if (event.chunkCount() <= 0) {
            job.markFailed("EMPTY_DOCUMENT");
            jobRepository.save(job);
            return IngestionResult.REJECTED_EMPTY_DOCUMENT;
        }

        job.markEmbedding();
        for (int i = 0; i < event.chunkCount(); i++) {
            // The embedder owns the call out to the model and the vector-store
            // write. If it throws, the transaction rolls back and the message is
            // redelivered — no half-ingested document is left visible.
            embedder.embed(event.docId(), i);
            job.recordChunkEmbedded();
        }
        job.markCompleted();
        if (log.isDebugEnabled()) {
            log.debug("Ingested doc {} ({} chunks)", event.docId(), event.chunkCount());
        }
        return IngestionResult.COMPLETED;
    }
}
