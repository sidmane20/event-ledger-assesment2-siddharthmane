package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * The body the gateway sends to the Account Service's
 * {@code POST /accounts/{accountId}/transactions} endpoint. {@code accountId}
 * travels in the path, not here. This is the gateway's view of the contract.
 */
public record AccountTransactionRequest(
        String eventId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Map<String, Object> metadata
) {
}
