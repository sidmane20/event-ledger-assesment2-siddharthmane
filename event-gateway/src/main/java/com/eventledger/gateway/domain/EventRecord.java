package com.eventledger.gateway.domain;

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
 * The gateway's local record of a submitted event. {@code eventId} is unique,
 * which is what makes {@code POST /events} idempotent: a repeated submission
 * resolves to the stored record instead of creating a second one.
 *
 * <p>Records are kept regardless of whether the Account Service was reachable,
 * so {@code GET /events/{id}} and {@code GET /events?account=} continue to work
 * from local data even when the downstream service is down.
 */
@Entity
@Table(
        name = "event_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_id", columnNames = "event_id"),
        indexes = {
                @Index(name = "idx_account_id", columnList = "account_id"),
                @Index(name = "idx_account_event_ts", columnList = "account_id, event_timestamp")
        }
)
public class EventRecord {

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

    @Lob
    @Column(name = "metadata_json", updatable = false)
    private String metadataJson;

    /** When the gateway received and stored the event. */
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status;

    protected EventRecord() {
        // for JPA
    }

    public EventRecord(String eventId, String accountId, TransactionType type, BigDecimal amount,
                       String currency, Instant eventTimestamp, String metadataJson,
                       Instant receivedAt, EventStatus status) {
        this.eventId = eventId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.eventTimestamp = eventTimestamp;
        this.metadataJson = metadataJson;
        this.receivedAt = receivedAt;
        this.status = status;
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

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }
}
