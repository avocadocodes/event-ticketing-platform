package com.nikita.ticketing.booking.dto;

import com.nikita.ticketing.booking.domain.Booking;

import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        String eventId,
        String customerId,
        int seatCount,
        String status,
        LocalDateTime createdAt
) {
    public static BookingResponse fromEntity(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getEventId(),
                booking.getCustomerId(),
                booking.getSeatCount(),
                booking.getStatus().name(),
                booking.getCreatedAt()
        );
    }
}
