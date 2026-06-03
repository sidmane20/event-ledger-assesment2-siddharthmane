package com.eventledger.gateway.web;

import com.eventledger.gateway.client.AccountBalanceResult;
import com.eventledger.gateway.client.AccountServiceClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxies balance queries to the Account Service. Unlike the event read
 * endpoints (served from local data), a balance depends entirely on the
 * downstream service — so when it is unreachable this returns 503 with a clear
 * message (handled by {@link GlobalExceptionHandler}) rather than stale data.
 */
@RestController
public class AccountQueryController {

    private final AccountServiceClient accountServiceClient;

    public AccountQueryController(AccountServiceClient accountServiceClient) {
        this.accountServiceClient = accountServiceClient;
    }

    @GetMapping("/accounts/{accountId}/balance")
    public AccountBalanceResult getBalance(@PathVariable String accountId) {
        return accountServiceClient.getBalance(accountId);
    }
}
