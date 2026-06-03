package com.eventledger.gateway.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.eventledger.gateway.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The Account Service's response to applying a transaction. {@code outcome} is
 * {@code APPLIED} or {@code DUPLICATE}. Unknown fields are ignored so the
 * services can evolve their contracts independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountApplyResult(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant appliedAt,
        String outcome
) {
}
