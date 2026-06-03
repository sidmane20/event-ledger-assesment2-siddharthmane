package com.eventledger.account.web;

import com.eventledger.account.api.ApplyOutcome;
import com.eventledger.account.api.TransactionResponse;
import com.eventledger.account.domain.TransactionType;
import com.eventledger.account.service.AccountNotFoundException;
import com.eventledger.account.service.AccountService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests: status-code mapping, validation responses, and error
 * handling. The service is mocked so this focuses on HTTP behaviour.
 */
@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    // Required by GlobalExceptionHandler, which is picked up by the slice.
    @MockitoBean
    private Tracer tracer;

    private TransactionResponse stored(ApplyOutcome outcome) {
        return new TransactionResponse("evt-001", "acct-123", TransactionType.CREDIT,
                new BigDecimal("150.00"), "USD", Instant.parse("2026-05-15T14:02:11Z"),
                Instant.parse("2026-05-15T14:02:12Z"), outcome);
    }

    private String validBody() {
        return """
                {"eventId":"evt-001","type":"CREDIT","amount":150.00,"currency":"USD",
                 "eventTimestamp":"2026-05-15T14:02:11Z"}
                """;
    }

    @Test
    void appliedTransactionReturns201() throws Exception {
        when(accountService.applyTransaction(eq("acct-123"), any())).thenReturn(stored(ApplyOutcome.APPLIED));

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.eventId").value("evt-001"));
    }

    @Test
    void duplicateTransactionReturns200() throws Exception {
        when(accountService.applyTransaction(eq("acct-123"), any())).thenReturn(stored(ApplyOutcome.DUPLICATE));

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DUPLICATE"));
    }

    @Test
    void missingRequiredFieldReturns400WithFieldErrors() throws Exception {
        String body = """
                {"type":"CREDIT","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """;

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("eventId"));
    }

    @Test
    void zeroAmountReturns400() throws Exception {
        String body = """
                {"eventId":"evt-001","type":"CREDIT","amount":0,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """;

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTransactionTypeReturns400() throws Exception {
        String body = """
                {"eventId":"evt-001","type":"FOO","amount":150.00,"currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """;

        mockMvc.perform(post("/accounts/acct-123/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void balanceForUnknownAccountReturns404() throws Exception {
        when(accountService.getBalance("missing")).thenThrow(new AccountNotFoundException("missing"));

        mockMvc.perform(get("/accounts/missing/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
