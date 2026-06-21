package com.hermes.worker.service;

public enum LedgerResult {
    APPLIED,
    REJECTED_INSUFFICIENT_FUNDS,
    REJECTED_UNKNOWN_ACCOUNT,
    SKIPPED_DUPLICATE
}
