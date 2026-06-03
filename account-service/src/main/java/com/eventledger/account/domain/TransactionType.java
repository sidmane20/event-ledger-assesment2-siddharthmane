package com.eventledger.account.domain;

/**
 * The kind of movement a transaction represents.
 * A CREDIT increases the account balance; a DEBIT decreases it.
 */
public enum TransactionType {
    CREDIT,
    DEBIT
}
