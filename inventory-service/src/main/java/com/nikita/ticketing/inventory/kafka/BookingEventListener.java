package com.nikita.ticketing.inventory.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.ticketing.inventory.events.BookingCreatedEvent;
import com.nikita.ticketing.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public BookingEventListener(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "bookings",
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBookingEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            String eventType = node.path("eventType").asText("");

            switch (eventType) {
                case "BookingCreated" -> {
                    BookingCreatedEvent event = objectMapper.treeToValue(node, BookingCreatedEvent.class);
                    inventoryService.processBooking(event);
                }
                case "BookingConfirmed" -> {
                    Long bookingId = node.path("bookingId").asLong();
                    String inventoryEventId = node.path("eventId").asText();
                    inventoryService.handleBookingConfirmed(bookingId, inventoryEventId);
                }
                case "BookingPaymentFailed" -> {
                    Long bookingId = node.path("bookingId").asLong();
                    inventoryService.handleBookingPaymentFailed(bookingId);
                }
                default -> log.warn("Unknown booking event type: '{}', skipping", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process booking event: {}", e.getMessage(), e);
        }
    }
}
