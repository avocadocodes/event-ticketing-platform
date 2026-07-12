package com.nikita.ticketing.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.ticketing.inventory.domain.EventInventory;
import com.nikita.ticketing.inventory.domain.ProcessedBooking;
import com.nikita.ticketing.inventory.domain.SeatHold;
import com.nikita.ticketing.inventory.dto.EventAvailabilityResponse;
import com.nikita.ticketing.inventory.events.BookingCreatedEvent;
import com.nikita.ticketing.inventory.events.SeatsRejectedEvent;
import com.nikita.ticketing.inventory.events.SeatsReservedEvent;
import com.nikita.ticketing.inventory.outbox.OutboxEvent;
import com.nikita.ticketing.inventory.outbox.OutboxEventRepository;
import com.nikita.ticketing.inventory.repository.EventInventoryRepository;
import com.nikita.ticketing.inventory.repository.ProcessedBookingRepository;
import com.nikita.ticketing.inventory.repository.SeatHoldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private static final int HOLD_MINUTES = 5;

    private final EventInventoryRepository inventoryRepository;
    private final ProcessedBookingRepository processedBookingRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public InventoryService(EventInventoryRepository inventoryRepository,
                            ProcessedBookingRepository processedBookingRepository,
                            SeatHoldRepository seatHoldRepository,
                            OutboxEventRepository outboxEventRepository,
                            ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.processedBookingRepository = processedBookingRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void processBooking(BookingCreatedEvent event) {
        if (processedBookingRepository.existsByBookingId(event.getBookingId())) {
            return;
        }

        boolean success = tryHold(event, false);
        if (!success) {
            try {
                success = tryHold(event, true);
            } catch (OptimisticLockingFailureException e) {
                success = false;
            }
        }

        processedBookingRepository.save(new ProcessedBooking(event.getBookingId()));

        if (success) {
            writeOutbox("inventory", event.getBookingId().toString(), "SeatsReserved",
                    new SeatsReservedEvent(event.getBookingId(), event.getEventId(), event.getSeatCount()));
        } else {
            writeOutbox("inventory", event.getBookingId().toString(), "SeatsRejected",
                    new SeatsRejectedEvent(event.getBookingId(), event.getEventId(), "Insufficient seats or event not found"));
        }
    }

    private boolean tryHold(BookingCreatedEvent event, boolean isRetry) {
        Optional<EventInventory> inventoryOpt = inventoryRepository.findByEventId(event.getEventId());
        if (inventoryOpt.isEmpty()) {
            return false;
        }
        EventInventory inventory = inventoryOpt.get();
        int activeHolds = seatHoldRepository.sumActiveHolds(event.getEventId(), LocalDateTime.now());
        // invariant: sold + activeHolds <= totalSeats
        // availableSeats tracks seats not yet sold; holds are separate
        int available = inventory.getAvailableSeats() - activeHolds;
        if (available < event.getSeatCount()) {
            return false;
        }
        seatHoldRepository.save(new SeatHold(
                event.getBookingId(), event.getEventId(), event.getSeatCount(),
                LocalDateTime.now().plusMinutes(HOLD_MINUTES)));
        // Touch the inventory row to acquire the optimistic lock version bump,
        // causing concurrent modifications to fail fast
        inventoryRepository.save(inventory);
        return true;
    }

    @Transactional
    public void handleBookingConfirmed(Long bookingId, String eventId) {
        Optional<SeatHold> holdOpt = seatHoldRepository.findById(bookingId);
        if (holdOpt.isEmpty() || holdOpt.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            // Hold expired — emit SeatsRejected so booking-service can mark as REJECTED
            log.warn("Hold missing or expired for confirmed booking {}, emitting SeatsRejected", bookingId);
            writeOutbox("inventory", bookingId.toString(), "SeatsRejected",
                    new SeatsRejectedEvent(bookingId, eventId, "Hold expired before confirmation"));
            return;
        }
        SeatHold hold = holdOpt.get();
        EventInventory inventory = inventoryRepository.findByEventId(hold.getEventId())
                .orElseThrow(() -> new NoSuchElementException("Inventory not found for event: " + hold.getEventId()));
        // Convert hold to sold: decrement availableSeats
        inventory.setAvailableSeats(inventory.getAvailableSeats() - hold.getSeats());
        inventoryRepository.save(inventory);
        seatHoldRepository.deleteById(bookingId);
    }

    @Transactional
    public void handleBookingPaymentFailed(Long bookingId) {
        seatHoldRepository.findById(bookingId).ifPresent(hold -> {
            seatHoldRepository.deleteById(bookingId);
            log.info("Released hold for payment-failed booking {}", bookingId);
        });
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void sweepExpiredHolds() {
        var expired = seatHoldRepository.findByExpiresAtBefore(LocalDateTime.now());
        if (!expired.isEmpty()) {
            seatHoldRepository.deleteAll(expired);
            log.info("Swept {} expired seat holds", expired.size());
        }
    }

    public EventAvailabilityResponse getEventAvailability(String eventId) {
        EventInventory inventory = inventoryRepository.findByEventId(eventId)
                .orElseThrow(() -> new NoSuchElementException("Inventory not found for event: " + eventId));
        int activeHolds = seatHoldRepository.sumActiveHolds(eventId, LocalDateTime.now());
        int effectiveAvailable = inventory.getAvailableSeats() - activeHolds;
        return new EventAvailabilityResponse(inventory.getEventId(), inventory.getTotalSeats(), effectiveAvailable);
    }

    private void writeOutbox(String aggregateType, String aggregateId, String eventType, Object payload) {
        String json;
        try {
            com.fasterxml.jackson.databind.node.ObjectNode envelope =
                    objectMapper.valueToTree(payload);
            envelope.put("eventType", eventType);
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize " + eventType, e);
        }
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(json);
        outboxEventRepository.save(outboxEvent);
    }
}
