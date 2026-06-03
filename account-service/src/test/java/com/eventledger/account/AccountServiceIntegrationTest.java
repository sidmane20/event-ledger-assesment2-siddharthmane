package com.eventledger.account;

import com.eventledger.account.api.AccountDetailsResponse;
import com.eventledger.account.api.ApplyOutcome;
import com.eventledger.account.api.BalanceResponse;
import com.eventledger.account.api.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-flow integration test against a running context and real H2:
 * apply → balance → idempotent duplicate → details, including out-of-order arrival.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountServiceIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private ResponseEntity<TransactionResponse> postTransaction(String accountId, String json) {
        RequestEntity<String> req = RequestEntity
                .post(URI.create("/accounts/" + accountId + "/transactions"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
        return rest.exchange(req, TransactionResponse.class);
    }

    @Test
    void fullFlowAcrossEndpoints() {
        String account = "acct-int-1";

        // Apply a credit -> 201 APPLIED
        ResponseEntity<TransactionResponse> applied = postTransaction(account, """
                {"eventId":"evt-int-1","type":"CREDIT","amount":150.00,"currency":"USD",
                 "eventTimestamp":"2026-05-15T14:02:11Z","metadata":{"source":"mainframe-batch"}}
                """);
        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(applied.getBody().outcome()).isEqualTo(ApplyOutcome.APPLIED);

        // Re-submit the same eventId -> 200 DUPLICATE, balance unchanged
        ResponseEntity<TransactionResponse> duplicate = postTransaction(account, """
                {"eventId":"evt-int-1","type":"CREDIT","amount":150.00,"currency":"USD",
                 "eventTimestamp":"2026-05-15T14:02:11Z"}
                """);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(duplicate.getBody().outcome()).isEqualTo(ApplyOutcome.DUPLICATE);

        // Out-of-order debit with an earlier timestamp
        postTransaction(account, """
                {"eventId":"evt-int-2","type":"DEBIT","amount":50.00,"currency":"USD",
                 "eventTimestamp":"2026-05-15T09:00:00Z"}
                """);

        // Balance = 150 - 50 = 100
        ResponseEntity<BalanceResponse> balance =
                rest.getForEntity("/accounts/" + account + "/balance", BalanceResponse.class);
        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balance.getBody().balance()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Account details list chronologically (earlier debit first)
        ResponseEntity<AccountDetailsResponse> details =
                rest.getForEntity("/accounts/" + account, AccountDetailsResponse.class);
        assertThat(details.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(details.getBody().transactionCount()).isEqualTo(2);
        assertThat(details.getBody().recentTransactions().get(0).eventId()).isEqualTo("evt-int-2");
    }

    @Test
    void balanceForUnknownAccountReturns404() {
        ResponseEntity<String> response =
                rest.getForEntity("/accounts/does-not-exist/balance", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
