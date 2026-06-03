package com.eventledger.gateway.api;

/**
 * Whether a submission created a new event record or matched one already stored
 * (idempotency on {@code eventId}).
 */
public enum SubmitOutcome {
    ACCEPTED,
    DUPLICATE
}
