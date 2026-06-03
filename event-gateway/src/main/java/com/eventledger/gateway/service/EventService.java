package com.eventledger.gateway.service;

import com.eventledger.gateway.api.EventResponse;
import com.eventledger.gateway.api.SubmitEventRequest;
import com.eventledger.gateway.api.SubmitOutcome;
import com.eventledger.gateway.domain.EventRecord;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.repository.EventRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 * Owns the gateway's local event store. Submission is idempotent on
 * {@code eventId}; reads are served entirely from local data so they keep
 * working even when the Account Service is unavailable.
 *
 * <p>At this stage the event is stored as {@link EventStatus#PENDING}; the
 * resilient call that forwards it to the Account Service and confirms it is
 * wired in a later step.
 */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public EventService(EventRecordRepository repository,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public EventResponse submitEvent(SubmitEventRequest request) {
        Optional<EventRecord> existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            return duplicate(existing.get());
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

        try {
            EventRecord saved = repository.saveAndFlush(record);
            recordReceived("accepted");
            log.info("Stored event eventId={} accountId={} type={} amount={} {} status={}",
                    saved.getEventId(), saved.getAccountId(), saved.getType(),
                    saved.getAmount(), saved.getCurrency(), saved.getStatus());
            return EventResponse.of(saved, SubmitOutcome.ACCEPTED);
        } catch (DataIntegrityViolationException race) {
            // Concurrent duplicate won the unique-constraint race; treat as duplicate.
            EventRecord winner = repository.findByEventId(request.eventId()).orElseThrow(() -> race);
            return duplicate(winner);
        }
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

    private EventResponse duplicate(EventRecord existing) {
        recordReceived("duplicate");
        log.info("Duplicate event ignored eventId={} accountId={}",
                existing.getEventId(), existing.getAccountId());
        return EventResponse.of(existing, SubmitOutcome.DUPLICATE);
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

    private void recordReceived(String outcome) {
        Counter.builder("gateway.events.received")
                .description("Number of events received by the gateway")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
