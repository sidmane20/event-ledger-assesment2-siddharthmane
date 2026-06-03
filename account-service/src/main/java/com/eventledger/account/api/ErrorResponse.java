package com.eventledger.account.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard error body. {@code traceId} lets a client correlate a failure with
 * the structured logs across both services.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String traceId,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }
}
