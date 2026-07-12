package com.nikita.ticketing.booking.events;

import java.io.Serializable;

public class SeatsRejectedEvent implements Serializable {

    private Long bookingId;
    private String eventId;
    private String reason;

    public SeatsRejectedEvent() {
    }

    public SeatsRejectedEvent(Long bookingId, String eventId, String reason) {
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.reason = reason;
    }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
