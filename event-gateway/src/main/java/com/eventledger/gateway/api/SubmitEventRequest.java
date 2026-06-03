package com.eventledger.gateway.api;

import com.eventledger.gateway.domain.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Request body for {@code POST /events}. Unlike the Account Service, the
 * gateway carries {@code accountId} in the body — this is the public payload
 * upstream systems submit.
 */
public record SubmitEventRequest(

        @NotBlank(message = "eventId is required")
        String eventId,

        @NotBlank(message = "accountId is required")
        String accountId,

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
