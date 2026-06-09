package com.hermes.worker.service;

public enum FulfillmentResult {
    FULFILLED,
    REJECTED_OUT_OF_STOCK,
    REJECTED_UNKNOWN_PRODUCT,
    SKIPPED_DUPLICATE
}
