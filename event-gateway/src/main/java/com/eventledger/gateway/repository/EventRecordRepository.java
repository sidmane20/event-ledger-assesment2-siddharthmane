package com.eventledger.gateway.repository;

import com.eventledger.gateway.domain.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRecordRepository extends JpaRepository<EventRecord, Long> {

    Optional<EventRecord> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    /** Events for an account in chronological order by the original event time. */
    List<EventRecord> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
