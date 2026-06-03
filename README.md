# Event Ledger

Two independently runnable Java microservices that process financial transaction
events, with attention to **observability, resiliency, and operational readiness**.

The system tolerates events that arrive **out of order** and events delivered
**more than once** (idempotency), and **degrades gracefully** when the internal
service is unavailable.

---

## Architecture

```
Browser / Client ──→  Event Gateway API  ──REST (sync)──→  Account Service
                      (public, :8080)                       (internal, :8081)
                      own H2 (eventdb)                       own H2 (accountdb)
```

- **Event Gateway** — public entry point. Validates input, enforces idempotency,
  stores an event record in its own database, and forwards the transaction to the
  Account Service through a resilient client.
- **Account Service** — owns account state (balances, transaction history). Called
  only by the gateway, never exposed to external clients.

The two services are **separate processes** with **separate in-memory H2
databases** — they share no database and no in-process state. A shared parent
`pom.xml` exists only to build them together; at runtime they are fully
independent.

### API contracts

**Event Gateway (`:8080`)**

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/events` | Submit a transaction event |
| `GET` | `/events/{id}` | Retrieve a single event by id |
| `GET` | `/events?account={accountId}` | List an account's events, ordered by event timestamp |
| `GET` | `/accounts/{accountId}/balance` | Balance (proxied to the Account Service) |
| `GET` | `/health` | Health check (status + DB connectivity) |
| `GET` | `/actuator/prometheus` | Metrics |

**Account Service (`:8081`)**

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/accounts/{accountId}/transactions` | Apply a transaction to an account |
| `GET` | `/accounts/{accountId}/balance` | Current balance |
| `GET` | `/accounts/{accountId}` | Account details + recent transactions |
| `GET` | `/health` | Health check (status + DB connectivity) |
| `GET` | `/actuator/prometheus` | Metrics |

### Event payload (`POST /events`)

```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": { "source": "mainframe-batch", "batchId": "B-9042" }
}
```

`type` must be `CREDIT` or `DEBIT`; `amount` must be greater than 0. A repeated
`eventId` returns the original event with a `DUPLICATE` outcome (`200`) and does
not change the balance.

---

## Prerequisites

- **Java 21+** (the build targets release 21; a newer JDK works too)
- **Maven 3.9+** (or use the system `mvn`)
- **Docker** (optional, only for the Docker Compose path)

---

## Build & test

A single command runs every test across both services:

```bash
mvn test
```

Covers idempotency, out-of-order tolerance, balance, validation, resiliency
(circuit breaker opens, 503 on downstream failure), trace propagation
(gateway → account service), and a full end-to-end flow.

Build the executable jars:

```bash
mvn clean package
```

---

## Running locally

Start each service in its own terminal:

```bash
# Terminal 1 — Account Service on :8081
mvn -pl account-service spring-boot:run

# Terminal 2 — Event Gateway on :8080
mvn -pl event-gateway spring-boot:run
```

The gateway reaches the account service at `http://localhost:8081` by default;
override with `ACCOUNT_SERVICE_BASE_URL`.

For human-readable (non-JSON) logs during local development, activate the
`local` profile, e.g. `-Dspring-boot.run.profiles=local`.

### Running with Docker Compose

```bash
docker compose up --build
```

Starts both services (gateway on `:8080`, account service on `:8081`), wires the
gateway to the account service over the compose network, and waits for the
account service to be healthy first.

---

## Try it

```bash
# Submit an event (201 Created, applied downstream)
curl -i -XPOST localhost:8080/events -H 'Content-Type: application/json' -d '{
  "eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,
  "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}'

# Re-submit the same eventId (200 OK, DUPLICATE, balance unchanged)
curl -i -XPOST localhost:8080/events -H 'Content-Type: application/json' -d '{
  "eventId":"evt-001","accountId":"acct-123","type":"CREDIT","amount":150.00,
  "currency":"USD","eventTimestamp":"2026-05-15T14:02:11Z"}'

# Out-of-order debit (earlier timestamp, arrives later)
curl -XPOST localhost:8080/events -H 'Content-Type: application/json' -d '{
  "eventId":"evt-002","accountId":"acct-123","type":"DEBIT","amount":50.00,
  "currency":"USD","eventTimestamp":"2026-05-15T09:00:00Z"}'

curl localhost:8080/events/evt-001                 # single event
curl "localhost:8080/events?account=acct-123"      # chronological listing
curl localhost:8080/accounts/acct-123/balance      # balance = 100.00
curl localhost:8080/health                          # health
```

---

## Observability

- **Structured logging** — JSON to stdout (`logstash-logback-encoder`) with
  `timestamp`, `level`, `service`, `traceId`, `spanId`, and message.
- **Distributed tracing** — a trace id is created at the gateway and propagated to
  the account service via the W3C `traceparent` header; both services log the same
  `traceId`, so one client request is traceable across both.
- **Health checks** — `GET /health` on both services returns overall status plus a
  database-connectivity diagnostic.
- **Metrics** — Micrometer + `/actuator/prometheus`. Custom metrics:
  - `gateway.events.received{outcome=accepted|duplicate}`
  - `ledger.transactions.applied{type, outcome=applied|duplicate}`

---

## Resiliency

The gateway's call to the account service is wrapped, from the outside in, by:

1. **Retry** — 3 attempts with **exponential backoff + jitter**, retrying only
   transient failures (timeouts, 5xx). 4xx and an open circuit are not retried.
2. **Circuit breaker** — opens when the recent failure rate crosses 50% (over a
   10-call window, min 5 calls), so the gateway **fails fast** instead of piling
   requests onto a struggling downstream. It auto-probes (half-open) after 10s.
3. **Timeout** — connect/read timeouts (2s) bound how long any single attempt may
   block.

**Why this combination:** a timeout alone can't recover from a blip; retry alone
can hammer a service that is genuinely down; a circuit breaker alone doesn't help
with brief transient errors. Together they ride out short hiccups (retry) while
protecting both services during a real outage (breaker), and never hang (timeout).
Implemented with [Resilience4j](https://resilience4j.readme.io/).

### Graceful degradation

When the Account Service is unavailable:

| Operation | Behaviour |
|---|---|
| `POST /events` | Returns **503** with a clear message (not a hang or 500); the event is still stored locally as `PENDING`. |
| `GET /events/{id}` | **Works** — served from the gateway's local data. |
| `GET /events?account=` | **Works** — served from the gateway's local data. |
| `GET /accounts/{id}/balance` | Returns **503** indicating the account service is unreachable. |
| `GET /health` (gateway) | Stays **UP** — it reflects only the gateway's own database. |

A previously `PENDING` event that is re-submitted is forwarded again, so it can be
completed once the downstream recovers.

---

## Project structure

```
event-ledger/
├── pom.xml                 # parent (build aggregation only)
├── docker-compose.yml
├── account-service/        # internal service — account state
│   ├── Dockerfile
│   └── src/...
└── event-gateway/          # public service — event intake + forwarding
    ├── Dockerfile
    └── src/...
```

---

## Notes on technology choices

- **Spring Boot 3.5 / Java 21** — actuator, validation, and Micrometer tracing
  come integrated. The build targets release 21 for broad compatibility.
- **H2 in-memory** per service — satisfies the "embedded DB" requirement and makes
  the health-check DB diagnostic meaningful.
- **HTTP/1.1 client** — the downstream client is pinned to HTTP/1.1; HTTP/2 brings
  nothing to this small internal synchronous call.
- **Explicit trace propagation** — the `traceparent` header is set by an
  interceptor rather than relying on auto-instrumentation, keeping propagation
  predictable.
```
