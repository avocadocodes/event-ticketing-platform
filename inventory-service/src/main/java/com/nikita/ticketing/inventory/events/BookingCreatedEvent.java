package com.nikita.ticketing.inventory.events;

import java.io.Serializable;

public class BookingCreatedEvent implements Serializable {

    private Long bookingId;
    private String eventId;
    private String customerId;
    private int seatCount;
    private String timestamp;

    public BookingCreatedEvent() {
    }

    public BookingCreatedEvent(Long bookingId, String eventId, String customerId, int seatCount, String timestamp) {
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.customerId = customerId;
        this.seatCount = seatCount;
        this.timestamp = timestamp;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public int getSeatCount() {
        return seatCount;
    }

    public void setSeatCount(int seatCount) {
        this.seatCount = seatCount;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
