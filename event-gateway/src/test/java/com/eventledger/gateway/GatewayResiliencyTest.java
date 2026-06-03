package com.eventledger.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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

import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resiliency tests: when the Account Service fails, the gateway returns 503
 * (not 500 or a hang), the circuit breaker opens to fail fast, and reads served
 * from local data keep working.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayResiliencyTest {

    private static final WireMockServer ACCOUNT_SERVICE = new WireMockServer(options().dynamicPort());

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

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
    void resetState() {
        ACCOUNT_SERVICE.resetAll();
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
        // Account Service consistently fails downstream.
        ACCOUNT_SERVICE.stubFor(post(urlPathMatching("/accounts/.*/transactions"))
                .willReturn(aResponse().withStatus(500)));
    }

    private ResponseEntity<String> submit(String eventId) {
        String body = """
                {"eventId":"%s","accountId":"acct-r","type":"CREDIT","amount":10.00,
                 "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}
                """.formatted(eventId);
        return rest.exchange(RequestEntity.post(URI.create("/events"))
                .contentType(MediaType.APPLICATION_JSON).body(body), String.class);
    }

    @Test
    void downstreamFailureReturns503AndOpensCircuit() {
        // Enough failing submissions to cross the breaker's minimum-calls threshold.
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> response = submit("evt-fail-" + i);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        }

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("accountService");
        assertThat(breaker.getState())
                .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN);
    }

    @Test
    void openCircuitFailsFastWithoutCallingDownstream() {
        circuitBreakerRegistry.circuitBreaker("accountService")
                .transitionToOpenState(); // force open

        int before = ACCOUNT_SERVICE.findAll(
                postRequestedFor(urlPathMatching("/accounts/.*/transactions"))).size();

        ResponseEntity<String> response = submit("evt-fastfail");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        int after = ACCOUNT_SERVICE.findAll(
                postRequestedFor(urlPathMatching("/accounts/.*/transactions"))).size();
        assertThat(after).isEqualTo(before); // breaker open -> no downstream call
    }

    @Test
    void localReadsStillWorkWhenDownstreamIsDown() {
        // The submission fails downstream (503) but is stored locally as PENDING.
        ResponseEntity<String> submitResponse = submit("evt-local");
        assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        ResponseEntity<String> read = rest.getForEntity("/events/evt-local", String.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).contains("\"status\":\"PENDING\"");

        ResponseEntity<String> list = rest.getForEntity("/events?account=acct-r", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("evt-local");
    }
}
