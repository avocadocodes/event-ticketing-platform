CREATE TABLE payments (
    id                UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    booking_id        BIGINT       NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    currency          VARCHAR(10)  NOT NULL,
    method            VARCHAR(255) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    gateway_reference VARCHAR(255),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);
