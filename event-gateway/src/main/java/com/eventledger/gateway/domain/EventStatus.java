package com.eventledger.gateway.domain;

/**
 * Tracks how far an event got in processing. The gateway always stores the
 * event locally (so reads keep working), then records whether the Account
 * Service confirmed it.
 *
 * <ul>
 *   <li>{@code APPLIED}  — the Account Service accepted the transaction.</li>
 *   <li>{@code PENDING}  — stored locally but not yet confirmed downstream
 *       (e.g. the Account Service was unreachable); a candidate for later retry.</li>
 * </ul>
 */
public enum EventStatus {
    APPLIED,
    PENDING
}
