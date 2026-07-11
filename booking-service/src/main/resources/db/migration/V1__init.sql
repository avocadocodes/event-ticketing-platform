CREATE TABLE bookings (
    id         BIGSERIAL PRIMARY KEY,
    event_id   VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    seat_count INT          NOT NULL,
    status     VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);
