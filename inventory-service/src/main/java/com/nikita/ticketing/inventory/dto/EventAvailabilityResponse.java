package com.nikita.ticketing.inventory.dto;

public record EventAvailabilityResponse(String eventId, int totalSeats, int availableSeats) {
}
