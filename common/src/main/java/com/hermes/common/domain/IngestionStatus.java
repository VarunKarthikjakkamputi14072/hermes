package com.hermes.common.domain;

/**
 * Lifecycle of a document ingestion job as it flows through the engine.
 *
 * PENDING   -> accepted by the API, published to Kafka, not yet picked up
 * EMBEDDING -> a worker is chunking + embedding the document
 * COMPLETED -> all chunks embedded and written to the vector store
 * FAILED    -> the document could not be ingested (e.g. empty / unsupported)
 */
public enum IngestionStatus {
    PENDING,
    EMBEDDING,
    COMPLETED,
    FAILED
}
