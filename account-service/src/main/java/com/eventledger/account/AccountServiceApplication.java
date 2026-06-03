package com.eventledger.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Account Service — internal service that owns account state (balances and
 * transaction history). It is called only by the Event Gateway and runs as an
 * independent process with its own in-memory H2 database.
 */
@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
