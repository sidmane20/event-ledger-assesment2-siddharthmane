package com.eventledger.gateway.api;

import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representation of a stored event returned to clients. {@code outcome} is only
 * populated by the submit endpoint to distinguish a fresh record from a
 * duplicate; it is null for plain reads.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventResponse(
        String eventId,
        String accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        Instant eventTimestamp,
        Instant receivedAt,
        EventStatus status,
        SubmitOutcome outcome
) {

    public static EventResponse of(EventRecord record, SubmitOutcome outcome) {
        return new EventResponse(
                record.getEventId(),
                record.getAccountId(),
                record.getType(),
                record.getAmount(),
                record.getCurrency(),
                record.getEventTimestamp(),
                record.getReceivedAt(),
                record.getStatus(),
                outcome
        );
    }

    public static EventResponse of(EventRecord record) {
        return of(record, null);
    }
}
