package com.eventledger.account.service;

import com.eventledger.account.api.ApplyOutcome;
import com.eventledger.account.api.ApplyTransactionRequest;
import com.eventledger.account.api.AccountDetailsResponse;
import com.eventledger.account.api.BalanceResponse;
import com.eventledger.account.api.TransactionResponse;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.repository.AccountTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Core-functionality tests for the account service: idempotency, balance
 * computation, and out-of-order tolerance. Uses a real repository against H2.
 */
@DataJpaTest
class AccountServiceTest {

    @Autowired
    private AccountTransactionRepository repository;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(repository, new ObjectMapper(), new SimpleMeterRegistry());
    }

    private ApplyTransactionRequest request(String eventId, TransactionType type, String amount, Instant ts) {
        return new ApplyTransactionRequest(eventId, type, new BigDecimal(amount), "USD", ts, null);
    }

    @Test
    void appliesACreditAndComputesBalance() {
        service.applyTransaction("acct-1", request("evt-1", TransactionType.CREDIT, "150.00", Instant.parse("2026-05-15T14:02:11Z")));

        BalanceResponse balance = service.getBalance("acct-1");
        assertThat(balance.balance()).isEqualByComparingTo("150.00");
        assertThat(balance.currency()).isEqualTo("USD");
    }

    @Test
    void balanceIsCreditsMinusDebits() {
        service.applyTransaction("acct-1", request("evt-1", TransactionType.CREDIT, "200.00", Instant.parse("2026-05-15T10:00:00Z")));
        service.applyTransaction("acct-1", request("evt-2", TransactionType.DEBIT, "75.50", Instant.parse("2026-05-15T11:00:00Z")));
        service.applyTransaction("acct-1", request("evt-3", TransactionType.CREDIT, "25.50", Instant.parse("2026-05-15T12:00:00Z")));

        assertThat(service.getBalance("acct-1").balance()).isEqualByComparingTo("150.00");
    }

    @Test
    void duplicateEventIdIsIdempotentAndDoesNotAlterBalance() {
        ApplyTransactionRequest req = request("evt-1", TransactionType.CREDIT, "150.00", Instant.parse("2026-05-15T14:02:11Z"));

        TransactionResponse first = service.applyTransaction("acct-1", req);
        TransactionResponse second = service.applyTransaction("acct-1", req);

        assertThat(first.outcome()).isEqualTo(ApplyOutcome.APPLIED);
        assertThat(second.outcome()).isEqualTo(ApplyOutcome.DUPLICATE);
        assertThat(second.eventId()).isEqualTo("evt-1");
        assertThat(service.getBalance("acct-1").balance()).isEqualByComparingTo("150.00");
        assertThat(repository.countByAccountId("acct-1")).isEqualTo(1);
    }

    @Test
    void balanceIsCorrectRegardlessOfArrivalOrder() {
        // Later-timestamped event arrives first, earlier one arrives second.
        service.applyTransaction("acct-1", request("evt-late", TransactionType.DEBIT, "40.00", Instant.parse("2026-05-15T18:00:00Z")));
        service.applyTransaction("acct-1", request("evt-early", TransactionType.CREDIT, "100.00", Instant.parse("2026-05-15T09:00:00Z")));

        assertThat(service.getBalance("acct-1").balance()).isEqualByComparingTo("60.00");
    }

    @Test
    void accountDetailsListsTransactionsInChronologicalOrder() {
        service.applyTransaction("acct-1", request("evt-late", TransactionType.CREDIT, "10.00", Instant.parse("2026-05-15T18:00:00Z")));
        service.applyTransaction("acct-1", request("evt-early", TransactionType.CREDIT, "20.00", Instant.parse("2026-05-15T09:00:00Z")));
        service.applyTransaction("acct-1", request("evt-mid", TransactionType.CREDIT, "30.00", Instant.parse("2026-05-15T12:00:00Z")));

        AccountDetailsResponse details = service.getAccount("acct-1");
        List<String> orderedEventIds = details.recentTransactions().stream().map(TransactionResponse::eventId).toList();

        assertThat(orderedEventIds).containsExactly("evt-early", "evt-mid", "evt-late");
        assertThat(details.transactionCount()).isEqualTo(3);
        assertThat(details.balance()).isEqualByComparingTo("60.00");
    }

    @Test
    void persistsAndReturnsMetadataAccount() {
        ApplyTransactionRequest req = new ApplyTransactionRequest(
                "evt-meta", TransactionType.CREDIT, new BigDecimal("5.00"), "USD",
                Instant.parse("2026-05-15T14:02:11Z"), Map.of("source", "mainframe-batch", "batchId", "B-9042"));

        service.applyTransaction("acct-meta", req);

        assertThat(repository.findByEventId("evt-meta")).isPresent()
                .get()
                .extracting("metadataJson").asString()
                .contains("mainframe-batch");
    }

    @Test
    void balanceForUnknownAccountThrowsNotFound() {
        assertThatThrownBy(() -> service.getBalance("missing"))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
