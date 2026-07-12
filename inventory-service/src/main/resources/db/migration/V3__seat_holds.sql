CREATE TABLE seat_holds (
    booking_id  BIGINT    NOT NULL PRIMARY KEY,
    event_id    VARCHAR(255) NOT NULL,
    seats       INT       NOT NULL,
    expires_at  TIMESTAMP NOT NULL
);

CREATE INDEX idx_seat_holds_event_id ON seat_holds (event_id);
CREATE INDEX idx_seat_holds_expires_at ON seat_holds (expires_at);
