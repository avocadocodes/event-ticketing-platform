CREATE TABLE outbox_events (
    id             UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(255) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        TEXT         NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    published      BOOLEAN      NOT NULL DEFAULT FALSE
);
