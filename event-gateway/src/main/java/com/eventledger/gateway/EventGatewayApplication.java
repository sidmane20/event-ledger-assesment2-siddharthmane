package com.eventledger.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Event Gateway — the public-facing entry point. It validates incoming
 * transaction events, enforces idempotency, stores an event record in its own
 * in-memory database, and forwards the transaction to the internal Account
 * Service. Runs as an independent process with its own H2 database.
 */
@SpringBootApplication
public class EventGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventGatewayApplication.class, args);
    }
}
