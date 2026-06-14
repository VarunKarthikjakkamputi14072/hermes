package com.hermes.worker.service;

import com.hermes.common.event.IngestRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates ingestion of one document. Deliberately <em>not</em> transactional:
 * it claims the job, then embeds each chunk and flushes progress through
 * {@link IngestionProgressService} in small committed steps, so the SSE stream
 * sees the counter climb live and a crash can resume mid-document. The order
 * path's single-transaction model isn't a fit here — a 5,000-page embed shouldn't
 * be one all-or-nothing transaction.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionProgressService progress;
    private final Embedder embedder;
    private final int flushEvery;

    public IngestionService(IngestionProgressService progress,
                            Embedder embedder,
                            @Value("${hermes.worker.progress-flush-every:1}") int flushEvery) {
        this.progress = progress;
        this.embedder = embedder;
        this.flushEvery = Math.max(1, flushEvery);
    }

    public IngestionResult ingest(IngestRequestedEvent event) {
        IngestionProgressService.Claim claim = progress.claim(event.jobId(), event.chunkCount());
        switch (claim.decision()) {
            case NOT_VISIBLE -> throw new JobNotYetVisibleException(event.jobId());
            case DUPLICATE -> {
                return IngestionResult.SKIPPED_DUPLICATE;
            }
            case EMPTY -> {
                return IngestionResult.REJECTED_EMPTY_DOCUMENT;
            }
            case PROCEED -> { /* fall through to the embed loop */ }
        }

        int total = event.chunkCount();
        for (int i = claim.startIndex(); i < total; i++) {
            // The embedder owns the call out to the model and the vector-store
            // write. If it throws, the message is redelivered; progress committed
            // so far lets the retry resume rather than re-embed from scratch.
            embedder.embed(event.docId(), i);
            int done = i + 1;
            if (done % flushEvery == 0) {
                progress.advance(event.jobId(), done);
            }
        }
        progress.complete(event.jobId(), total);
        if (log.isDebugEnabled()) {
            log.debug("Ingested doc {} ({} chunks)", event.docId(), total);
        }
        return IngestionResult.COMPLETED;
    }
}
