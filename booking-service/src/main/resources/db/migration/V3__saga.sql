ALTER TABLE bookings ADD COLUMN amount NUMERIC(19, 4);

CREATE TABLE processed_events (
    id         BIGSERIAL PRIMARY KEY,
    event_key  VARCHAR(255) NOT NULL UNIQUE,
    processed_at TIMESTAMP NOT NULL DEFAULT now()
);
