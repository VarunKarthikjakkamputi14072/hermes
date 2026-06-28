package com.hermes.common.domain;

/**
 * The deterministic outcome of the risk rules. This is decided by code, never by
 * the LLM — the model only writes the human-readable explanation for a flag.
 */
public enum RiskDecision {
    ALLOW,
    REVIEW,
    HOLD
}
