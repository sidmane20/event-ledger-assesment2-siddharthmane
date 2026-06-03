package com.eventledger.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single transaction applied to an account.
 *
 * <p>The natural key is {@code eventId}: it carries a unique constraint so the
 * same upstream event can never be stored twice. This makes the apply operation
 * idempotent at the database layer, even under concurrent duplicate delivery.
 */
@Entity
@Table(
        name = "account_transaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_id", columnNames = "event_id"),
        indexes = {
                @Index(name = "idx_account_id", columnList = "account_id"),
                @Index(name = "idx_account_event_ts", columnList = "account_id, event_timestamp")
        }
)
public class AccountTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private String eventId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    /** When the event originally occurred upstream (drives chronological ordering). */
    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant eventTimestamp;

    /** Optional upstream context, persisted as a JSON string. */
    @Lob
    @Column(name = "metadata_json", updatable = false)
    private String metadataJson;

    /** When this service recorded the transaction. */
    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    protected AccountTransaction() {
        // for JPA
    }

    public AccountTransaction(String eventId, String accountId, TransactionType type, BigDecimal amount,
                              String currency, Instant eventTimestamp, String metadataJson, Instant appliedAt) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.metadataJson = metadataJson;
        this.appliedAt = appliedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
