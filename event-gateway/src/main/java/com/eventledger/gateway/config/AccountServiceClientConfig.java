package com.eventledger.gateway.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the {@link RestClient} used to call the Account Service, with timeouts
 * and distributed-trace propagation.
 */
@Configuration
public class AccountServiceClientConfig {

    @Bean
    public RestClient accountServiceRestClient(
            RestClient.Builder builder,
            ObservationRegistry observationRegistry,
            ClientHttpRequestInterceptor tracePropagationInterceptor,
            @Value("${account-service.base-url}") String baseUrl,
            @Value("${account-service.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${account-service.read-timeout-ms}") long readTimeoutMs) {

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        // Pin to a plain HTTP/1.1 factory. The JDK client that detect() would
        // pick negotiates HTTP/2, which is unnecessary for this internal call
        // and trips up some servers (h2c stream cancellation).
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.simple().build(settings);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .observationRegistry(observationRegistry)
                .requestInterceptor(tracePropagationInterceptor)
                .build();
    }

    /**
     * Injects the current trace context into outgoing requests as a W3C
     * {@code traceparent} header, so the Account Service joins the same trace.
     * This is explicit rather than relying on auto-instrumentation, which keeps
     * propagation predictable and easy to reason about.
     */
    @Bean
    public ClientHttpRequestInterceptor tracePropagationInterceptor(CurrentTraceContext currentTraceContext) {
        return (request, body, execution) -> {
            TraceContext context = currentTraceContext.context();
            if (context != null) {
                String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
                String traceparent = "00-" + context.traceId() + "-" + context.spanId() + "-" + flags;
                request.getHeaders().set("traceparent", traceparent);
            }
            return execution.execute(request, body);
        };
    }
}
