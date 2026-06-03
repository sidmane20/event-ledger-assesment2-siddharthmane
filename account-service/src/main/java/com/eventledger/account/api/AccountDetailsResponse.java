package com.eventledger.account.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * Account snapshot: current balance plus recent transactions ordered
 * chronologically by their original event timestamp.
 */
public record AccountDetailsResponse(
        String accountId,
        BigDecimal balance,
        String currency,
        long transactionCount,
        List<TransactionResponse> recentTransactions
) {
}
