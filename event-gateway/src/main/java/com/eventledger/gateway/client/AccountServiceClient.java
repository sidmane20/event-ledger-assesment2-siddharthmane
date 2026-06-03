package com.eventledger.gateway.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Resilient client for the Account Service.
 *
 * <p>Each call is wrapped, from the outside in, by:
 * <ol>
 *   <li><b>Retry</b> with exponential backoff + jitter — retries only transient
 *       failures (timeouts, 5xx); 4xx and an open circuit are not retried.</li>
 *   <li><b>Circuit breaker</b> — trips open when the downstream failure rate is
 *       high, so we fail fast instead of piling onto a struggling service.</li>
 *   <li>A connect/read <b>timeout</b> on the underlying HTTP client.</li>
 * </ol>
 *
 * The fallback sits on the outer {@code @Retry} so it only fires once retries
 * are exhausted or the breaker is open. It rethrows 4xx unchanged (a caller
 * error) and converts everything else into {@link AccountServiceUnavailableException}.
 */
@Component
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);
    private static final String INSTANCE = "accountService";

    private final RestClient restClient;

    public AccountServiceClient(RestClient accountServiceRestClient) {
        this.restClient = accountServiceRestClient;
    }

    @Retry(name = INSTANCE, fallbackMethod = "applyFallback")
    @CircuitBreaker(name = INSTANCE)
    public AccountApplyResult applyTransaction(String accountId, AccountTransactionRequest request) {
        return restClient.post()
                .uri("/accounts/{accountId}/transactions", accountId)
                .body(request)
                .retrieve()
                .body(AccountApplyResult.class);
    }

    @Retry(name = INSTANCE, fallbackMethod = "balanceFallback")
    @CircuitBreaker(name = INSTANCE)
    public AccountBalanceResult getBalance(String accountId) {
        return restClient.get()
                .uri("/accounts/{accountId}/balance", accountId)
                .retrieve()
                .body(AccountBalanceResult.class);
    }

    @SuppressWarnings("unused")
    private AccountApplyResult applyFallback(String accountId, AccountTransactionRequest request, Throwable t) {
        if (t instanceof HttpClientErrorException clientError) {
            throw clientError;
        }
        log.warn("Account Service apply failed for accountId={} eventId={}: {}",
                accountId, request.eventId(), t.toString());
        throw new AccountServiceUnavailableException("apply transaction", t);
    }

    @SuppressWarnings("unused")
    private AccountBalanceResult balanceFallback(String accountId, Throwable t) {
        if (t instanceof HttpClientErrorException clientError) {
            throw clientError;
        }
        log.warn("Account Service balance lookup failed for accountId={}: {}", accountId, t.toString());
        throw new AccountServiceUnavailableException("read balance", t);
    }
}
