package com.nikita.ticketing.inventory.events;

import java.io.Serializable;

public class SeatsReservedEvent implements Serializable {

    private Long bookingId;
    private String eventId;
    private int seatsReserved;

    public SeatsReservedEvent() {
    }

    public SeatsReservedEvent(Long bookingId, String eventId, int seatsReserved) {
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.seatsReserved = seatsReserved;
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

    public int getSeatsReserved() {
        return seatsReserved;
    }

    public void setSeatsReserved(int seatsReserved) {
        this.seatsReserved = seatsReserved;
    }
}
