package com.eventledger.gateway.domain;

/**
 * The kind of movement an event represents. Mirrors the Account Service enum;
 * the two services own their contracts independently and do not share code.
 */
public enum TransactionType {
    CREDIT,
    DEBIT
}
