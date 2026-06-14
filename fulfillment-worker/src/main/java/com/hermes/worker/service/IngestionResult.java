package com.hermes.worker.service;

public enum IngestionResult {
    COMPLETED,
    REJECTED_EMPTY_DOCUMENT,
    SKIPPED_DUPLICATE
}
