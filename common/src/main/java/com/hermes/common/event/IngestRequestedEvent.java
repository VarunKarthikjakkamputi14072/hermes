package com.hermes.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable message published when the API accepts a document for ingestion and
 * consumed by the ingestion worker. Like {@link OrderPlacedEvent} it stays small:
 * it carries only what the worker needs to fetch the document and embed its
 * chunks. The bytes themselves live in object storage / the vector store, not on
 * the wire.
 */
public record IngestRequestedEvent(
        UUID jobId,
        String docId,
        String source,
        int chunkCount,
        Instant requestedAt
) {
}
