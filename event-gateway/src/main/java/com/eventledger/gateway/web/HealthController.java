package com.eventledger.gateway.web;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public {@code /health} endpoint. Reports overall status plus a database
 * connectivity diagnostic.
 *
 * <p>It intentionally checks only the gateway's own database, not the Account
 * Service: the gateway stays healthy and keeps serving local reads even when
 * the downstream service is down (graceful degradation).
 */
@RestController
public class HealthController {

    private final DataSource dataSource;
    private final String serviceName;

    public HealthController(DataSource dataSource, Environment env) {
        this.dataSource = dataSource;
        this.serviceName = env.getProperty("spring.application.name", "event-gateway");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean dbUp = isDatabaseReachable();

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("database", dbUp ? "UP" : "DOWN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbUp ? "UP" : "DOWN");
        body.put("service", serviceName);
        body.put("timestamp", Instant.now().toString());
        body.put("checks", checks);

        HttpStatus status = dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }

    private boolean isDatabaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception e) {
            return false;
        }
    }
}
