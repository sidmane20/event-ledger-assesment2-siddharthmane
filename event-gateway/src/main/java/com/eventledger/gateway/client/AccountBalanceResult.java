package com.eventledger.gateway.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/** The Account Service's balance response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountBalanceResult(
        String accountId,
        BigDecimal balance,
        String currency,
        Instant asOf
) {
}
