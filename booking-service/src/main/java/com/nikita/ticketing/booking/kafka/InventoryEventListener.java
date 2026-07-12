package com.nikita.ticketing.booking.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.ticketing.booking.events.SeatsRejectedEvent;
import com.nikita.ticketing.booking.events.SeatsReservedEvent;
import com.nikita.ticketing.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final BookingService bookingService;
    private final ObjectMapper objectMapper;

    public InventoryEventListener(BookingService bookingService, ObjectMapper objectMapper) {
        this.bookingService = bookingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory", groupId = "booking-service-inventory-group",
            containerFactory = "inventoryKafkaListenerContainerFactory")
    public void onInventoryEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText();
            Long bookingId = node.path("bookingId").asLong();
            String eventId = node.path("eventId").asText();

            switch (eventType) {
                case "SeatsReserved" -> {
                    int seatsReserved = node.path("seatsReserved").asInt();
                    bookingService.handleSeatsReserved(new SeatsReservedEvent(bookingId, eventId, seatsReserved));
                }
                case "SeatsRejected" -> {
                    String reason = node.path("reason").asText();
                    bookingService.handleSeatsRejected(new SeatsRejectedEvent(bookingId, eventId, reason));
                }
                default -> log.warn("Unknown inventory event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", e.getMessage(), e);
        }
    }
}
