package com.nikita.ticketing.inventory.events;

import java.io.Serializable;

public class BookingPaymentFailedEvent implements Serializable {

    private Long bookingId;
    private String eventId;

    public BookingPaymentFailedEvent() {
    }

    public BookingPaymentFailedEvent(Long bookingId, String eventId) {
        this.bookingId = bookingId;
        this.eventId = eventId;
    }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
}
