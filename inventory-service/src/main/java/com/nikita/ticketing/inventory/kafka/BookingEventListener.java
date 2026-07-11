package com.nikita.ticketing.inventory.kafka;

import com.nikita.ticketing.inventory.events.BookingCreatedEvent;
import com.nikita.ticketing.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventListener {

    private final InventoryService inventoryService;

    public BookingEventListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(
            topics = "bookings",
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBookingCreated(BookingCreatedEvent event) {
        inventoryService.processBooking(event);
    }
}
