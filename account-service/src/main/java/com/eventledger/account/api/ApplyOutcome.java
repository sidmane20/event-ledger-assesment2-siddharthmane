package com.eventledger.account.api;

/**
 * Whether a transaction was newly applied or recognised as a duplicate of one
 * already stored (idempotency).
 */
public enum ApplyOutcome {
    APPLIED,
    DUPLICATE
}
