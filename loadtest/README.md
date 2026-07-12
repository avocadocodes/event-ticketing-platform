# Load Test — Oversell Concurrency Proof

## What it tests

`oversell-test.js` fires 300 concurrent booking requests (configurable) at a
single event through the API gateway, then reads inventory availability and
asserts that `sold + active_holds <= total_seats` at all times.

## Prerequisites

The full docker compose stack must be running:

```bash
docker compose up --build
```

Wait for all services to report healthy before running the test.

## Run with docker (grafana/k6 image)

```bash
docker run --rm \
  --network event-ticketing-platform_default \
  -e BASE_URL=http://api-gateway:8080 \
  -e EVENT_ID=EVT-001 \
  -e BOOKINGS=300 \
  -v "$(pwd)/loadtest:/scripts" \
  grafana/k6:latest run /scripts/oversell-test.js
```

`event-ticketing-platform_default` is the default Docker Compose network name.
Adjust if you gave your compose project a custom name.

## Environment variables

| Variable   | Default                 | Description                          |
|------------|-------------------------|--------------------------------------|
| `BASE_URL`  | `http://localhost:8080` | Gateway URL                          |
| `EVENT_ID`  | `EVT-001`               | Event to book against (seeded with 100 seats) |
| `BOOKINGS`  | `300`                   | Total booking iterations             |

## Interpreting results

The test defines a `oversell_violations` counter threshold of `count==0`.
If the threshold is breached, k6 exits with a non-zero code and prints
`OVERSELL DETECTED` to stdout.

A passing run looks like:

```
Total seats: 100
Available (excluding holds): 47
Used or held: 53
Invariant holds: sold+holds (53) <= total (100)
```

## Load test results

*(numbers to be filled after k6 run)*
