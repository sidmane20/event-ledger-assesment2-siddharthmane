package com.eventledger.gateway;

import com.eventledger.gateway.client.AccountBalanceResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathTemplate;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-flow integration test: a real gateway context talking to a WireMock stub
 * standing in for the Account Service. Covers the happy path across endpoints,
 * idempotency, ordering, the balance proxy, and trace-ID propagation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final WireMockServer ACCOUNT_SERVICE = new WireMockServer(options().dynamicPort());

    @Autowired
    private TestRestTemplate rest;

    @DynamicPropertySource
    static void accountServiceProperties(DynamicPropertyRegistry registry) {
        ACCOUNT_SERVICE.start();
        registry.add("account-service.base-url", () -> "http://localhost:" + ACCOUNT_SERVICE.port());
    }

    @AfterAll
    static void stopWireMock() {
        ACCOUNT_SERVICE.stop();
    }

    @BeforeEach
    void stubAccountService() {
        ACCOUNT_SERVICE.resetAll();
        ACCOUNT_SERVICE.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"eventId\":\"e\",\"outcome\":\"APPLIED\"}")));
        ACCOUNT_SERVICE.stubFor(get(urlPathTemplate("/accounts/{id}/balance"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"acct-1\",\"balance\":100.00,\"currency\":\"USD\"}")));
    }

    private ResponseEntity<String> submit(String json) {
        return rest.exchange(RequestEntity.post(URI.create("/events"))
                .contentType(MediaType.APPLICATION_JSON).body(json), String.class);
    }

    @Test
    void fullFlowAcrossEndpoints() {
        ResponseEntity<String> accepted = submit("""
                {"eventId":"evt-1","accountId":"acct-1","type":"CREDIT","amount":150.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Idempotent re-submission
        ResponseEntity<String> duplicate = submit("""
                {"eventId":"evt-1","accountId":"acct-1","type":"CREDIT","amount":150.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Out-of-order debit (earlier timestamp, arrives second)
        submit("""
                {"eventId":"evt-2","accountId":"acct-1","type":"DEBIT","amount":50.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T09:00:00Z"}
                """);

        // Listing is chronological by eventTimestamp
        ResponseEntity<String> list = rest.getForEntity("/events?account=acct-1", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().indexOf("evt-2")).isLessThan(list.getBody().indexOf("evt-1"));

        // Balance proxy returns the downstream value
        ResponseEntity<AccountBalanceResult> balance =
                rest.getForEntity("/accounts/acct-1/balance", AccountBalanceResult.class);
        assertThat(balance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(balance.getBody().balance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void propagatesTraceContextToAccountService() {
        submit("""
                {"eventId":"evt-trace","accountId":"acct-1","type":"CREDIT","amount":10.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """);

        // The downstream call must carry the W3C traceparent header.
        ACCOUNT_SERVICE.verify(postRequestedFor(urlPathMatching("/accounts/.*/transactions"))
                .withHeader("traceparent", matching("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")));
    }

    @Test
    void getUnknownEventReturns404() {
        ResponseEntity<String> response = rest.getForEntity("/events/nope", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
