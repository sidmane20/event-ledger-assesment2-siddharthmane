package com.eventledger.gateway.service;

/** Raised when an event is requested by an id the gateway has never seen. */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventId) {
        super("No event found with id '" + eventId + "'");
    }
}
