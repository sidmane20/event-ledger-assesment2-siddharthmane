package com.eventledger.account.api;

import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representation of a stored transaction returned to callers. {@code outcome}
 * is only populated on the apply endpoint to distinguish a fresh write from a
 * duplicate; it is null for plain listings.
 */
public record TransactionResponse(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant appliedAt,
        ApplyOutcome outcome
) {

    public static TransactionResponse of(AccountTransaction tx, ApplyOutcome outcome) {
        return new TransactionResponse(
                tx.getEventId(),
                tx.getAccountId(),
                tx.getType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getEventTimestamp(),
                tx.getAppliedAt(),
                outcome
        );
    }

    public static TransactionResponse of(AccountTransaction tx) {
        return of(tx, null);
    }
}
