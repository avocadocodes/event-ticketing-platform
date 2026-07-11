# Event Ticketing Platform

A small, event-driven ticketing backend that shows how to keep a write-heavy
booking flow responsive while a separate service owns seat inventory. Services
talk to each other asynchronously over Kafka rather than through synchronous
HTTP calls, so a slow or restarting inventory node never blocks a customer from
placing a booking.

## Problem Statement

Selling tickets is a classic contention problem: many customers try to grab a
limited pool of seats at the same time. A naive design puts booking creation
and seat decrement in the same synchronous transaction, which couples the two
concerns and makes the booking endpoint only as available as the inventory
database.

This project separates the two responsibilities:

- **booking-service** accepts a booking, persists it in the `RESERVED` state,
  and emits a `BookingCreated` event. It returns to the caller immediately.
- **inventory-service** owns seat counts. It consumes `BookingCreated`, applies
  the seat decrement idempotently, and publishes `SeatsReserved` or
  `SeatsRejected` so downstream consumers can react.

The trade-off is eventual consistency: a booking exists before its seats are
confirmed. That is acceptable here and is the same model most high-volume
ticketing systems use — reserve first, confirm asynchronously.

## Architecture

```
                              ┌──────────────────┐
                              │   API Gateway    │  :8080
                              │ (Spring Cloud    │
                              │   Gateway)       │
                              └───────┬──────────┘
                    /bookings/**      │      /inventory/**
                 ┌────────────────────┴─────────────────────┐
                 ▼                                           ▼
        ┌──────────────────┐                       ┌──────────────────┐
        │ booking-service  │  :8081                │ inventory-service│  :8082
        │                  │                       │                  │
        │  REST + JPA      │                       │  REST + JPA      │
        └───────┬──────────┘                       └────────┬─────────┘
                │                                            │
                ▼                                            ▼
        ┌──────────────┐                            ┌──────────────┐
        │  booking_db  │                            │ inventory_db │
        │ (PostgreSQL) │                            │ (PostgreSQL) │
        └──────────────┘                            └──────────────┘
                │                                            ▲
                │  publish "bookings"                        │ consume "bookings"
                │       BookingCreated                       │
                └───────────────────►┌──────────┐────────────┘
                                     │  Kafka   │
                                     └────┬─────┘
                                          │  publish "inventory"
                                          ▼   SeatsReserved / SeatsRejected
                                   (downstream consumers)
```

Three deployable modules plus a parent aggregator:

| Module            | Port | Responsibility                                            |
|-------------------|------|-----------------------------------------------------------|
| `api-gateway`     | 8080 | Single entry point; routes to the two backend services    |
| `booking-service` | 8081 | Create/list bookings; produce `BookingCreated`            |
| `inventory-service` | 8082 | Own seat inventory; consume bookings; produce results     |

## Event Flow

1. A client `POST`s a booking through the gateway to `booking-service`.
2. `booking-service` saves the booking as `RESERVED` and publishes a
   `BookingCreated` event to the Kafka topic **`bookings`**, keyed by booking id.
3. `inventory-service` consumes from **`bookings`**. For each event it:
   - checks the `processed_bookings` table — if the booking id was already
     handled, it stops (idempotency);
   - looks up the event's inventory row;
   - if seats are available, decrements `available_seats`, records the booking
     as processed, and publishes `SeatsReserved` to the **`inventory`** topic;
   - if the event is unknown or seats are insufficient, it records the booking
     as processed and publishes `SeatsRejected` instead.

Because the consumer is idempotent, Kafka's at-least-once delivery is safe: a
redelivered event is recognised and skipped.

## Key Decisions

- **Database per service.** `booking-service` and `inventory-service` each own
  a PostgreSQL database and never share tables. This keeps their schemas
  independent and enforces the service boundary.
- **Flyway migrations per service.** Each service ships its own
  `V1__init.sql`, so schema changes are versioned and applied on startup with
  `ddl-auto: validate` guarding against drift.
- **Idempotent consumer.** Seat decrements are tracked by booking id in a
  dedicated `processed_bookings` table rather than relying on exactly-once
  delivery semantics from the broker.
- **Optimistic locking.** `event_inventory` carries a JPA `@Version` column so
  concurrent decrements fail fast instead of silently overselling.
- **Duplicated event DTOs.** Each service defines its own copy of the event
  classes. This avoids a shared library that would couple the services'
  release cycles; the wire contract is JSON, and the deserializer ignores type
  headers so the two copies interoperate.
- **Reactive gateway.** `api-gateway` uses Spring Cloud Gateway on WebFlux and
  deliberately excludes `spring-boot-starter-web` to avoid a servlet/reactive
  stack clash.

## How to Run

Everything is wired together with Docker Compose — Kafka, ZooKeeper, both
databases, and all three services:

```bash
docker compose up --build
```

The gateway is then available on `http://localhost:8080`.

To build and test the whole project locally without Docker:

```bash
mvn -B verify
```

Tests run against in-memory H2 with Kafka auto-configuration excluded, so no
broker or database is required for `mvn test`.

## API Examples (through the gateway)

Create a booking:

```bash
curl -X POST http://localhost:8080/bookings \
  -H "Content-Type: application/json" \
  -d '{"eventId":"EVT-001","customerId":"CUST-42","seatCount":3}'
```

List all bookings:

```bash
curl http://localhost:8080/bookings
```

Fetch a single booking:

```bash
curl http://localhost:8080/bookings/1
```

Check seat availability for an event (the inventory service is seeded with
`EVT-001`, `EVT-002`, and `EVT-003`):

```bash
curl http://localhost:8080/inventory/EVT-001/availability
```

A moment after creating the booking above, the same availability call reflects
the decremented seat count — that is the asynchronous flow completing.

## Observability

Every service exposes Spring Boot Actuator with a Micrometer Prometheus
registry:

- Health: `/actuator/health` (details always shown)
- Prometheus scrape endpoint: `/actuator/prometheus`
- Metrics browser: `/actuator/metrics`

These are reachable per service (ports 8080/8081/8082) and the gateway also
publishes its own actuator endpoints. Point a Prometheus instance at the
`/actuator/prometheus` endpoints to collect JVM, HTTP, and Kafka client
metrics; the health endpoints are suitable as container liveness/readiness
probes.

Interactive API documentation (springdoc-openapi / Swagger UI) is available on
the two business services at `/swagger-ui.html`.

## Project Layout

```
event-ticketing-platform/
├── pom.xml                     parent aggregator + dependency management
├── docker-compose.yml
├── api-gateway/
├── booking-service/
│   └── src/main/java/com/nikita/ticketing/booking/
│       ├── controller/  service/  repository/  domain/  dto/  events/  config/  exception/
└── inventory-service/
    └── src/main/java/com/nikita/ticketing/inventory/
        ├── controller/  service/  repository/  domain/  dto/  events/  config/  kafka/  exception/
```
