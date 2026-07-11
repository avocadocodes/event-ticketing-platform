CREATE TABLE event_inventory (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(255) NOT NULL UNIQUE,
    total_seats     INT         NOT NULL,
    available_seats INT         NOT NULL,
    version         BIGINT      DEFAULT 0
);

CREATE TABLE processed_bookings (
    id           BIGSERIAL PRIMARY KEY,
    booking_id   BIGINT    NOT NULL UNIQUE,
    processed_at TIMESTAMP NOT NULL
);

INSERT INTO event_inventory (event_id, total_seats, available_seats)
VALUES ('EVT-001', 100, 100),
       ('EVT-002', 50, 50),
       ('EVT-003', 200, 200);
