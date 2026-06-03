package com.eventledger.gateway.client;

/**
 * Raised when the Account Service cannot be reached or the circuit breaker is
 * open. The web layer maps this to {@code 503 Service Unavailable} so callers
 * get a clear "try again later" signal instead of a hang or a 500.
 */
public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(String operation, Throwable cause) {
        super("Account Service is unavailable while attempting to " + operation, cause);
    }
}
