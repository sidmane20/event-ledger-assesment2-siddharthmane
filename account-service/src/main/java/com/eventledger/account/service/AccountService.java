package com.eventledger.account.service;

import com.eventledger.account.api.AccountDetailsResponse;
import com.eventledger.account.api.ApplyOutcome;
import com.eventledger.account.api.ApplyTransactionRequest;
import com.eventledger.account.api.BalanceResponse;
import com.eventledger.account.api.TransactionResponse;
import com.eventledger.account.domain.AccountTransaction;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.repository.AccountTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns account state. Applying a transaction is idempotent on {@code eventId};
 * balances are computed as sum(CREDIT) − sum(DEBIT), which is independent of
 * the order events arrive in.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    /** Cap on how many transactions are returned in an account snapshot. */
    private static final int RECENT_LIMIT = 20;

    private final AccountTransactionRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AccountService(AccountTransactionRepository repository,
                          ObjectMapper objectMapper,
                          MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Apply a transaction to an account. If a transaction with the same
     * {@code eventId} already exists, the stored transaction is returned
     * unchanged and the balance is not affected.
     */
    @Transactional
    public TransactionResponse applyTransaction(String accountId, ApplyTransactionRequest request) {
        Optional<AccountTransaction> existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            return duplicate(existing.get());
        }

        AccountTransaction tx = new AccountTransaction(
                request.eventId(),
                accountId,
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                serializeMetadata(request.metadata()),
                Instant.now()
        );

        try {
            AccountTransaction saved = repository.saveAndFlush(tx);
            recordApplied(saved.getType());
            log.info("Applied transaction eventId={} accountId={} type={} amount={} {}",
                    saved.getEventId(), accountId, saved.getType(), saved.getAmount(), saved.getCurrency());
            return TransactionResponse.of(saved, ApplyOutcome.APPLIED);
        } catch (DataIntegrityViolationException race) {
            // A concurrent duplicate won the unique-constraint race; treat as duplicate.
            AccountTransaction winner = repository.findByEventId(request.eventId())
                    .orElseThrow(() -> race);
            return duplicate(winner);
        }
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        requireAccountExists(accountId);
        BigDecimal balance = currentBalance(accountId);
        String currency = inferCurrency(accountId);
        return new BalanceResponse(accountId, balance, currency, Instant.now());
    }

    @Transactional(readOnly = true)
    public AccountDetailsResponse getAccount(String accountId) {
        requireAccountExists(accountId);
        List<AccountTransaction> ordered = repository.findByAccountIdOrderByEventTimestampAsc(accountId);

        List<AccountTransaction> recent = ordered.size() > RECENT_LIMIT
                ? ordered.subList(ordered.size() - RECENT_LIMIT, ordered.size())
                : ordered;

        List<TransactionResponse> recentDtos = recent.stream()
                .map(TransactionResponse::of)
                .toList();

        BigDecimal balance = currentBalance(accountId);
        String currency = ordered.isEmpty() ? null : ordered.get(0).getCurrency();
        return new AccountDetailsResponse(accountId, balance, currency, ordered.size(), recentDtos);
    }

    private TransactionResponse duplicate(AccountTransaction existing) {
        recordDuplicate(existing.getType());
        log.info("Duplicate transaction ignored eventId={} accountId={}",
                existing.getEventId(), existing.getAccountId());
        return TransactionResponse.of(existing, ApplyOutcome.DUPLICATE);
    }

    private void requireAccountExists(String accountId) {
        if (!repository.existsByAccountId(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
    }

    private BigDecimal currentBalance(String accountId) {
        BigDecimal balance = repository.computeBalance(accountId, TransactionType.CREDIT);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    private String inferCurrency(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId).stream()
                .findFirst()
                .map(AccountTransaction::getCurrency)
                .orElse(null);
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            // Metadata is best-effort context; never fail the transaction over it.
            log.warn("Could not serialize metadata; storing null", e);
            return null;
        }
    }

    private void recordApplied(TransactionType type) {
        Counter.builder("ledger.transactions.applied")
                .description("Number of transactions processed by the account service")
                .tag("type", type.name())
                .tag("outcome", "applied")
                .register(meterRegistry)
                .increment();
    }

    private void recordDuplicate(TransactionType type) {
        Counter.builder("ledger.transactions.applied")
                .description("Number of transactions processed by the account service")
                .tag("type", type.name())
                .tag("outcome", "duplicate")
                .register(meterRegistry)
                .increment();
    }
}
