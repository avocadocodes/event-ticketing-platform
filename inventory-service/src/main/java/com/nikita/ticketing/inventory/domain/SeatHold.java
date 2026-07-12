package com.nikita.ticketing.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_holds")
public class SeatHold {

    @Id
    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false)
    private int seats;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public SeatHold() {
    }

    public SeatHold(Long bookingId, String eventId, int seats, LocalDateTime expiresAt) {
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.seats = seats;
        this.expiresAt = expiresAt;
    }

    public Long getBookingId() { return bookingId; }
    public String getEventId() { return eventId; }
    public int getSeats() { return seats; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
