package com.nikita.ticketing.booking.events;

import java.io.Serializable;

public class BookingConfirmedEvent implements Serializable {

    private Long bookingId;
    private String eventId;

    public BookingConfirmedEvent() {
    }

    public BookingConfirmedEvent(Long bookingId, String eventId) {
        this.bookingId = bookingId;
        this.eventId = eventId;
    }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
}
