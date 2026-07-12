package com.nikita.ticketing.booking.domain;

public enum BookingStatus {
    PENDING_RESERVATION,
    AWAITING_PAYMENT,
    CONFIRMED,
    REJECTED,
    PAYMENT_FAILED,
    CANCELLED
}
