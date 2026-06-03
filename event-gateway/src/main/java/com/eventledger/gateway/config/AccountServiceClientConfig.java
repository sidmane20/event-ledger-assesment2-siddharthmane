package com.eventledger.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Builds the {@link RestClient} used to call the Account Service.
 *
 * <p>It starts from the auto-configured {@link RestClient.Builder}, which is
 * already instrumented for tracing — so the W3C {@code traceparent} header is
 * propagated downstream automatically. Connect/read timeouts bound how long any
 * single attempt may block, which is the "timeout" half of the resiliency story.
 */
@Configuration
public class AccountServiceClientConfig {

    @Bean
    public RestClient accountServiceRestClient(
            RestClient.Builder builder,
            @Value("${account-service.base-url}") String baseUrl,
            @Value("${account-service.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${account-service.read-timeout-ms}") long readTimeoutMs) {

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
