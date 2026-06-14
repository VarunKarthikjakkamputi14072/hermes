package com.hermes.worker.service;

import java.util.UUID;

/**
 * Thrown when an ingestion event is consumed before the producing transaction's
 * commit is visible. It is retryable — the listener's error handler will redeliver
 * after a back-off, by which time the row has been committed. The same dual-write
 * race the order path guards against with {@code OrderNotYetVisibleException}.
 */
public class JobNotYetVisibleException extends RuntimeException {

    public JobNotYetVisibleException(UUID jobId) {
        super("Ingestion job " + jobId + " not yet visible; will retry");
    }
}
