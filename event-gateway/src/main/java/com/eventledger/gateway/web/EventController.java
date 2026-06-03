package com.eventledger.gateway.web;

import com.eventledger.gateway.api.EventResponse;
import com.eventledger.gateway.api.SubmitEventRequest;
import com.eventledger.gateway.api.SubmitOutcome;
import com.eventledger.gateway.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /**
     * Submit a transaction event. Returns 201 when newly accepted and 200 when
     * the {@code eventId} was already stored (idempotent duplicate).
     */
    @PostMapping
    public ResponseEntity<EventResponse> submit(@Valid @RequestBody SubmitEventRequest request) {
        EventResponse response = eventService.submitEvent(request);
        HttpStatus status = response.outcome() == SubmitOutcome.DUPLICATE
                ? HttpStatus.OK
                : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    /** Read a single event from local storage — works regardless of downstream availability. */
    @GetMapping("/{id}")
    public EventResponse getById(@PathVariable("id") String eventId) {
        return eventService.getEvent(eventId);
    }

    /**
     * List an account's events, ordered chronologically by event timestamp.
     * Served from local storage, so it works even if the Account Service is down.
     */
    @GetMapping(params = "account")
    public List<EventResponse> listByAccount(@RequestParam("account") String accountId) {
        return eventService.listEventsForAccount(accountId);
    }
}
