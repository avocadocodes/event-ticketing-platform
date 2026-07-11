package com.nikita.ticketing.inventory.service;

import com.nikita.ticketing.inventory.domain.EventInventory;
import com.nikita.ticketing.inventory.domain.ProcessedBooking;
import com.nikita.ticketing.inventory.dto.EventAvailabilityResponse;
import com.nikita.ticketing.inventory.events.BookingCreatedEvent;
import com.nikita.ticketing.inventory.events.SeatsRejectedEvent;
import com.nikita.ticketing.inventory.events.SeatsReservedEvent;
import com.nikita.ticketing.inventory.repository.EventInventoryRepository;
import com.nikita.ticketing.inventory.repository.ProcessedBookingRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class InventoryService {

    private static final String INVENTORY_TOPIC = "inventory";

    private final EventInventoryRepository inventoryRepository;
    private final ProcessedBookingRepository processedBookingRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryService(EventInventoryRepository inventoryRepository,
                            ProcessedBookingRepository processedBookingRepository,
                            KafkaTemplate<String, Object> kafkaTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.processedBookingRepository = processedBookingRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void processBooking(BookingCreatedEvent event) {
        if (processedBookingRepository.existsByBookingId(event.getBookingId())) {
            return;
        }

        Optional<EventInventory> inventoryOpt = inventoryRepository.findByEventId(event.getEventId());

        if (inventoryOpt.isEmpty() || inventoryOpt.get().getAvailableSeats() < event.getSeatCount()) {
            String reason = inventoryOpt.isEmpty() ? "Event not found" : "Insufficient seats";
            processedBookingRepository.save(new ProcessedBooking(event.getBookingId()));
            kafkaTemplate.send(INVENTORY_TOPIC, event.getBookingId().toString(),
                    new SeatsRejectedEvent(event.getBookingId(), event.getEventId(), reason));
            return;
        }

        EventInventory inventory = inventoryOpt.get();
        inventory.setAvailableSeats(inventory.getAvailableSeats() - event.getSeatCount());
        inventoryRepository.save(inventory);
        processedBookingRepository.save(new ProcessedBooking(event.getBookingId()));
        kafkaTemplate.send(INVENTORY_TOPIC, event.getBookingId().toString(),
                new SeatsReservedEvent(event.getBookingId(), event.getEventId(), event.getSeatCount()));
    }

    public EventAvailabilityResponse getEventAvailability(String eventId) {
        EventInventory inventory = inventoryRepository.findByEventId(eventId)
                .orElseThrow(() -> new NoSuchElementException("Inventory not found for event: " + eventId));
        return new EventAvailabilityResponse(inventory.getEventId(), inventory.getTotalSeats(), inventory.getAvailableSeats());
    }
}
