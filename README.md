# Event Ticketing Platform

A Kafka-backed microservices platform for booking event tickets, extended with a
choreography-based saga, transactional outbox, seat holds, and a payment leg.

## Architecture

```
                         ┌──────────────────────────┐
                         │       API Gateway         │  :8080
                         │   (Spring Cloud Gateway)  │
                         └──────┬─────────┬──────────┘
              /bookings/**      │         │ /inventory/**   /payments/**
        ┌─────────────────┐     │         │     ┌──────────────────────────┐
        ▼                 │     │         │     ▼                          │
┌──────────────────┐      │     │    ┌──────────────────┐  ┌────────────────────┐
│ booking-service  │:8081 │     │    │inventory-service │  │ payment-service    │
│                  │      │     │    │             :8082 │  │             :8083  │
│ REST + JPA       │      │     │    │ REST + JPA        │  │ REST + JPA + Redis │
│ Outbox relay     │      │     │    │ Outbox relay      │  │ Resilience4j GW    │
└───────┬──────────┘      │     │    └────────┬──────────┘  └────────────────────┘
        │                 │     │             │
        ▼                 │     │             ▼
┌──────────────┐          │     │    ┌──────────────┐  ┌────────────┐  ┌─────────────┐
│  booking_db  │          │     │    │ inventory_db │  │ payment_db │  │   Redis :6379│
│ (PostgreSQL) │          │     │    │ (PostgreSQL) │  │(PostgreSQL)│  └─────────────┘
└──────────────┘          │     │    └──────────────┘
                          │     │
        ┌─────────────────┘     └──────────────────────────────────────┐
        │                                                               │
        │  topic "bookings"                                             │
        │  BookingCreated / BookingConfirmed / BookingPaymentFailed     │
        ▼                                                               ▼
  ┌──────────┐   topic "inventory"                             inventory-service
  │  Kafka   │◄── SeatsReserved / SeatsRejected ──────────────────────►│
  └──────────┘                                              booking-service consumes
```

### Modules

| Module              | Port | Responsibility                                                    |
|---------------------|------|-------------------------------------------------------------------|
| `api-gateway`       | 8080 | Single entry point; routes to all three backend services          |
| `booking-service`   | 8081 | Create bookings; run saga steps; call payment-service             |
| `inventory-service` | 8082 | Own seat inventory; seat holds with TTL; saga compensation        |
| `payment-service`   | 8083 | Process payments; Redis idempotency; Resilience4j gateway         |

---

## Saga — Event Flow

### Happy path

```
Client
  │
  ├─POST /bookings──────────────────────────►booking-service
  │                                          PENDING_RESERVATION + BookingCreated → outbox
  │
  │   [outbox relay publishes to "bookings"]
  │
  │                                          inventory-service consumes BookingCreated
  │                                          → seat HOLD (5 min TTL), optimistic lock
  │                                          SeatsReserved → outbox
  │
  │   [outbox relay publishes to "inventory"]
  │
  │                                          booking-service consumes SeatsReserved
  │                                          → AWAITING_PAYMENT
  │                                          → POST /payments (Idempotency-Key=bookingId)
  │                                          payment-service: PENDING→SUCCEEDED
  │                                          booking-service: CONFIRMED + BookingConfirmed → outbox
  │
  │   [outbox relay publishes to "bookings"]
  │
  │                                          inventory-service consumes BookingConfirmed
  │                                          → hold converted to sold (availableSeats--)
  │
  └─GET /bookings/{id}  status: CONFIRMED
```

### Payment-failure compensation

```
  ... (same up to payment call)
  booking-service: payment returns FAILED (or circuit-breaker fallback)
  → PAYMENT_FAILED + BookingPaymentFailed → outbox

  [relay publishes to "bookings"]

  inventory-service consumes BookingPaymentFailed
  → seat hold DELETED (compensating transaction)
  seats are now available again
```

### Hold expiry (TTL path)

```
  inventory-service @Scheduled (30s sweeper)
  → deletes seat_holds rows where expires_at < now()
  → seats become implicitly available (not counted in active holds)

  If BookingConfirmed arrives after hold expiry:
  → inventory-service detects missing/expired hold
  → emits SeatsRejected to "inventory"
  → booking-service marks booking REJECTED
```

---

## Design Decisions

### Transactional Outbox (dual-write problem)

A service must atomically update its own database AND notify other services. Writing
directly to Kafka in the same business transaction is not possible without XA
transactions. The outbox pattern solves this: the domain change and a pending
outbox row are committed in the same local transaction. A separate `@Scheduled`
relay thread reads unpublished rows and publishes to Kafka, then marks them
published. On relay failure the row stays unpublished and retries on the next tick,
giving at-least-once delivery.

Consumers must therefore be idempotent — they check a `processed_bookings` or
`processed_events` table before acting.

### Choreography vs Orchestration

This saga uses choreography: each service reacts to events without a central
coordinator. This avoids a single point of failure and keeps services decoupled.
The trade-off is that the full saga flow is harder to visualise — it is spread
across multiple listener classes rather than one orchestrator class. Orchestration
(e.g. via a booking-service state machine) would give better observability at the
cost of coupling.

### Idempotent Consumers

Every consumer records processed event keys before acting. `inventory-service`
uses `processed_bookings (booking_id UNIQUE)`. `booking-service` uses
`processed_events (event_key UNIQUE)` where the key is `"{EventType}:{bookingId}"`.
On duplicate delivery, the consumer returns early without side effects.

### Optimistic-Lock No-Oversell Invariant

`EventInventory` carries a JPA `@Version` column. When two threads attempt to
update the same row concurrently, one will receive an `OptimisticLockingFailureException`.
`InventoryService.processBooking` retries once on that exception; if it still fails,
the booking is rejected. This prevents overselling at the database level without
row-level locks.

### Seat-Hold TTL

Seat holds are rows in `seat_holds (booking_id PK, event_id, seats, expires_at)`.
Available capacity is computed as `availableSeats - SUM(active_holds)`, preserving
the invariant `sold + active_holds <= total_seats`. A 30-second sweeper deletes
expired holds. If a `BookingConfirmed` arrives after the hold has expired, the
inventory service detects the missing hold and emits `SeatsRejected` so the
booking is marked `REJECTED`. This keeps the invariant correct at the cost of
occasionally rejecting a late confirmation.

### Unit Price Simplification

Booking amount = `seatCount × $25.00 USD`. This hardcoded price is intentional —
the platform has no event-pricing model. The constant lives in
`BookingService.SEAT_PRICE` and the simplification is documented here.

### Payment Resilience

`SimulatedGatewayClient` succeeds ~70% of the time. It is wrapped in:
- `@Retry(name="gateway", maxAttempts=3, waitDuration=200ms)` — transparently
  retries transient failures before the circuit breaker sees them.
- `@CircuitBreaker(name="gateway", fallbackMethod="chargeFallback")` — after
  50% failure rate in a 10-request sliding window, opens for 10 s and returns
  `GatewayResult(false, null)` immediately, triggering the `PAYMENT_FAILED`
  compensation path.

### Redis Idempotency (payment-service)

`payment-service` stores `idempotency:<key> → paymentId` in Redis with a 24-hour
TTL. On repeated requests with the same `Idempotency-Key` header, the existing
payment is returned without re-charging. In tests, Redis autoconfiguration is
excluded and `StringRedisTemplate` is mocked via Mockito.

---

## How to Run

```bash
docker compose up --build
```

The gateway is then available at `http://localhost:8080`.

Build and test without Docker (no broker or database required):

```bash
mvn -B verify
```

---

## API Examples (through the gateway)

Create a booking (starts the saga):

```bash
curl -X POST http://localhost:8080/bookings \
  -H "Content-Type: application/json" \
  -d '{"eventId":"EVT-001","customerId":"CUST-42","seatCount":3}'
```

Poll booking status:

```bash
curl http://localhost:8080/bookings/1
# status progresses: PENDING_RESERVATION → AWAITING_PAYMENT → CONFIRMED
```

Check seat availability (reflects active holds):

```bash
curl http://localhost:8080/inventory/EVT-001/availability
```

Submit a payment directly:

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: my-unique-key-001" \
  -d '{"bookingId":1,"amount":"75.00","currency":"USD","method":"CARD"}'
```

---

## Load Test

See [`loadtest/README.md`](loadtest/README.md) for instructions on running the
k6 concurrency/oversell proof.

## Load Test Results

Measured against the full docker compose stack (all four services + Kafka + three
Postgres instances + Redis) on a development laptop, k6 running in Docker with
50 concurrent VUs firing bookings through the API gateway.

**Run 1 — clean 50-seat event, 300 bookings (~450 seats demanded):**

| Metric | Result |
|---|---|
| Request throughput | 1,351 req/s |
| `POST /bookings` latency | med 34 ms · p95 66 ms · max 93 ms |
| Requests accepted | 300 / 300 |
| Final ledger | 39 seats sold + 0 active holds ≤ 50 capacity |
| CONFIRMED seat sum vs inventory sold count | exact match (39 = 39) |
| Payment failures | 7 bookings (11 seats) — every hold released by compensation |
| **Oversell violations** | **0** |

**Run 2 — 100-seat event under payment-gateway duress, 306 bookings (~458 seats demanded):**
the burst tripped the payment circuit breaker, producing 52 payment failures
(78 seats). Every failed booking's hold was released by the compensating
transaction — final state: 26 seats sold, 0 stuck holds, 0 oversell violations,
and the CONFIRMED seat sum again matched the inventory sold count exactly.

The invariant `sold + active_holds <= total_seats` held at every observation
point in both runs.

---

## Observability

Every service exposes Spring Boot Actuator:

- `GET /actuator/health` — liveness/readiness
- `GET /actuator/prometheus` — Prometheus metrics scrape endpoint
- `GET /actuator/metrics` — metrics browser

Ports: gateway 8080, booking-service 8081, inventory-service 8082, payment-service 8083.

Interactive API docs (Swagger UI) are available at `/swagger-ui.html` on the
three business services.

---

## Project Layout

```
event-ticketing-platform/
├── pom.xml                         parent aggregator + dependency management
├── docker-compose.yml
├── loadtest/
│   ├── oversell-test.js            k6 concurrency proof
│   └── README.md
├── api-gateway/
├── booking-service/
│   └── src/main/java/com/nikita/ticketing/booking/
│       ├── controller/  service/  repository/  domain/
│       ├── dto/  events/  config/  exception/
│       ├── outbox/          OutboxEvent, OutboxEventRepository, OutboxRelay
│       └── kafka/           InventoryEventListener
├── inventory-service/
│   └── src/main/java/com/nikita/ticketing/inventory/
│       ├── controller/  service/  repository/  domain/
│       ├── dto/  events/  config/  kafka/  exception/
│       └── outbox/          OutboxEvent, OutboxEventRepository, OutboxRelay
└── payment-service/
    └── src/main/java/com/nikita/ticketing/payment/
        ├── controller/  service/  repository/  domain/
        ├── dto/  gateway/  exception/
        └── PaymentServiceApplication.java
```
