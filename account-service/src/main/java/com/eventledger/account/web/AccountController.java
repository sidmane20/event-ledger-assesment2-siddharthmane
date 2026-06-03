package com.eventledger.account.web;

import com.eventledger.account.api.AccountDetailsResponse;
import com.eventledger.account.api.ApplyOutcome;
import com.eventledger.account.api.ApplyTransactionRequest;
import com.eventledger.account.api.BalanceResponse;
import com.eventledger.account.api.TransactionResponse;
import com.eventledger.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Apply a transaction. Returns 201 when newly applied and 200 when the
     * {@code eventId} was already recorded (idempotent duplicate).
     */
    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody ApplyTransactionRequest request) {

        TransactionResponse response = accountService.applyTransaction(accountId, request);
        HttpStatus status = response.outcome() == ApplyOutcome.DUPLICATE
                ? HttpStatus.OK
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{accountId}/balance")
    public BalanceResponse getBalance(@PathVariable String accountId) {
        return accountService.getBalance(accountId);
    }

    @GetMapping("/{accountId}")
    public AccountDetailsResponse getAccount(@PathVariable String accountId) {
        return accountService.getAccount(accountId);
    }
}
