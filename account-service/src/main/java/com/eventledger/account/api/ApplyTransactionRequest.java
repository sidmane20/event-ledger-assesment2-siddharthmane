package com.eventledger.account.api;

import com.eventledger.account.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Request body for applying a transaction to an account. {@code accountId} is
 * taken from the path, not the body. This is part of the API contract between
 * the Event Gateway and the Account Service.
 */
public record ApplyTransactionRequest(

        @NotBlank(message = "eventId is required")
        String eventId,

        @NotNull(message = "type is required and must be CREDIT or DEBIT")
        TransactionType type,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than 0")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency,

        @NotNull(message = "eventTimestamp is required")
        Instant eventTimestamp,

        Map<String, Object> metadata
) {
}
