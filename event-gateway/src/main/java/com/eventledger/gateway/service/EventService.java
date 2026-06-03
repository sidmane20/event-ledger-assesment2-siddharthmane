package com.eventledger.gateway.service;

import com.eventledger.gateway.api.EventResponse;
import com.eventledger.gateway.api.SubmitEventRequest;
import com.eventledger.gateway.api.SubmitOutcome;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.client.AccountServiceUnavailableException;
import com.eventledger.gateway.client.AccountTransactionRequest;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.repository.EventRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the gateway's local event store and forwards transactions to the Account
 * Service.
 *
 * <p>The event is always persisted locally first (as {@link EventStatus#PENDING}),
 * then forwarded. This ordering is deliberate: if the downstream call fails the
 * record survives, so reads keep working and the event can be reconciled later.
 * On a downstream outage the caller gets a 503 (via
 * {@link AccountServiceUnavailableException}) rather than a hang or a 500.
 *
 * <p>Submission is idempotent on {@code eventId}. A re-submitted event that is
 * still {@code PENDING} (a previous downstream failure) is retried; one already
 * {@code APPLIED} is a no-op.
 *
 * <p>Note: this method is intentionally <em>not</em> wrapped in a single
 * transaction, so the initial save commits independently of the downstream call.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final AccountServiceClient accountServiceClient;

    public EventService(EventRecordRepository repository,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry,
                        AccountServiceClient accountServiceClient) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.accountServiceClient = accountServiceClient;
    }

    public EventResponse submitEvent(SubmitEventRequest request) {
        Optional<EventRecord> existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            return handleDuplicate(existing.get());
        }

        EventRecord record = new EventRecord(
                request.eventId(),
                request.accountId(),
                request.type(),
                request.amount(),
                request.currency(),
                request.eventTimestamp(),
                serializeMetadata(request.metadata()),
                Instant.now(),
                EventStatus.PENDING
        );

        EventRecord saved;
        try {
            saved = repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException race) {
            // Concurrent duplicate won the unique-constraint race; treat as duplicate.
            EventRecord winner = repository.findByEventId(request.eventId()).orElseThrow(() -> race);
            return handleDuplicate(winner);
        }

        recordReceived("accepted");
        log.info("Stored event eventId={} accountId={} type={} amount={} {} status={}",
                saved.getEventId(), saved.getAccountId(), saved.getType(),
                saved.getAmount(), saved.getCurrency(), saved.getStatus());

        forwardToAccountService(saved); // may throw AccountServiceUnavailableException -> 503
        return EventResponse.of(saved, SubmitOutcome.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(String eventId) {
        return repository.findByEventId(eventId)
                .map(EventResponse::of)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEventsForAccount(String accountId) {
        return repository.findByAccountIdOrderByEventTimestampAsc(accountId).stream()
                .map(EventResponse::of)
                .toList();
    }

    private EventResponse handleDuplicate(EventRecord existing) {
        recordReceived("duplicate");
        log.info("Duplicate event eventId={} accountId={} status={}",
                existing.getEventId(), existing.getAccountId(), existing.getStatus());

        // A still-pending duplicate is a chance to complete a previously failed forward.
        if (existing.getStatus() == EventStatus.PENDING) {
            forwardToAccountService(existing); // may throw -> 503
        }
        return EventResponse.of(existing, SubmitOutcome.DUPLICATE);
    }

    /**
     * Forward the event to the Account Service through the resilient client. On
     * success the record is promoted to {@code APPLIED}; on a downstream outage
     * the exception propagates (record stays {@code PENDING}).
     */
    private void forwardToAccountService(EventRecord record) {
        AccountTransactionRequest downstreamRequest = new AccountTransactionRequest(
                record.getEventId(),
                record.getType(),
                record.getAmount(),
                record.getCurrency(),
                record.getEventTimestamp(),
                deserializeMetadata(record.getMetadataJson())
        );

        accountServiceClient.applyTransaction(record.getAccountId(), downstreamRequest);

        record.setStatus(EventStatus.APPLIED);
        repository.save(record);
        log.info("Event applied downstream eventId={} accountId={}",
                record.getEventId(), record.getAccountId());
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize metadata; storing null", e);
            return null;
        }
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Could not deserialize stored metadata; forwarding without it", e);
            return null;
        }
    }

    private void recordReceived(String outcome) {
        Counter.builder("gateway.events.received")
                .description("Number of events received by the gateway")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
