package com.eventledger.gateway.web;

import com.eventledger.gateway.api.EventResponse;
import com.eventledger.gateway.api.SubmitOutcome;
import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.client.AccountServiceUnavailableException;
import com.eventledger.gateway.domain.EventStatus;
import com.eventledger.gateway.domain.TransactionType;
import com.eventledger.gateway.service.EventNotFoundException;
import com.eventledger.gateway.service.EventService;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the gateway: status-code mapping, validation, and the
 * graceful-degradation 503 path. The service and downstream client are mocked.
 */
@WebMvcTest({EventController.class, AccountQueryController.class})
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private AccountServiceClient accountServiceClient;

    @MockitoBean
    private Tracer tracer;

    private EventResponse stored(SubmitOutcome outcome, EventStatus status) {
        return new EventResponse("evt-001", "acct-123", TransactionType.CREDIT,
                new BigDecimal("150.00"), "USD", Instant.parse("2026-05-15T14:02:11Z"),
                Instant.parse("2026-05-15T14:02:12Z"), status, outcome);
    }

    private String validBody() {
        return """
                {"eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """;
    }

    @Test
    void acceptedEventReturns201() throws Exception {
        when(eventService.submitEvent(any())).thenReturn(stored(SubmitOutcome.ACCEPTED, EventStatus.APPLIED));

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void duplicateEventReturns200() throws Exception {
        when(eventService.submitEvent(any())).thenReturn(stored(SubmitOutcome.DUPLICATE, EventStatus.APPLIED));

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
    }

    @Test
    void downstreamOutageReturns503() throws Exception {
        when(eventService.submitEvent(any()))
                .thenThrow(new AccountServiceUnavailableException("apply transaction", new RuntimeException("boom")));

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void missingRequiredFieldReturns400() throws Exception {
        String body = """
                {"accountId":"acct-123","type":"CREDIT","amount":150.00,"currency":"USD",
                 "eventTimestamp":"2026-05-15T14:02:11Z"}
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("eventId"));
    }

    @Test
    void zeroAmountReturns400() throws Exception {
        String body = """
                {"eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":0,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownEventReturns404() throws Exception {
        when(eventService.getEvent("missing")).thenThrow(new EventNotFoundException("missing"));

        mockMvc.perform(get("/events/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void balanceProxyReturns503WhenDownstreamUnavailable() throws Exception {
        when(accountServiceClient.getBalance("acct-123"))
                .thenThrow(new AccountServiceUnavailableException("read balance", new RuntimeException("down")));

        mockMvc.perform(get("/accounts/acct-123/balance"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("unavailable")));
    }
}
