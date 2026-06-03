package com.eventledger.account.service;

/** Raised when a query targets an account that has no transactions on record. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountId) {
        super("No account found with id '" + accountId + "'");
    }
}
