package com.nikita.ticketing.inventory.service;

import com.nikita.ticketing.inventory.domain.EventInventory;
import com.nikita.ticketing.inventory.domain.ProcessedBooking;
import com.nikita.ticketing.inventory.events.BookingCreatedEvent;
import com.nikita.ticketing.inventory.events.SeatsRejectedEvent;
import com.nikita.ticketing.inventory.events.SeatsReservedEvent;
import com.nikita.ticketing.inventory.repository.EventInventoryRepository;
import com.nikita.ticketing.inventory.repository.ProcessedBookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private EventInventoryRepository invRepo;

    @Mock
    private ProcessedBookingRepository processedRepo;

    @Mock
    private KafkaTemplate<String, Object> kafka;

    @InjectMocks
    private InventoryService service;

    @Test
    void processBookingDecrementsSeats() {
        BookingCreatedEvent event = new BookingCreatedEvent(1L, "EVT-001", "CUST-1", 5, "2026-01-01T00:00:00");

        EventInventory inventory = new EventInventory(null, "EVT-001", 100, 100, 0L);
        when(processedRepo.existsByBookingId(1L)).thenReturn(false);
        when(invRepo.findByEventId("EVT-001")).thenReturn(Optional.of(inventory));

        service.processBooking(event);

        ArgumentCaptor<EventInventory> inventoryCaptor = ArgumentCaptor.forClass(EventInventory.class);
        verify(invRepo).save(inventoryCaptor.capture());
        assertThat(inventoryCaptor.getValue().getAvailableSeats()).isEqualTo(95);

        verify(processedRepo).save(any(ProcessedBooking.class));
        verify(kafka).send(eq("inventory"), any(String.class), any(SeatsReservedEvent.class));
    }

    @Test
    void processBookingIdempotentSkips() {
        BookingCreatedEvent event = new BookingCreatedEvent(1L, "EVT-001", "CUST-1", 5, "2026-01-01T00:00:00");

        when(processedRepo.existsByBookingId(1L)).thenReturn(true);

        service.processBooking(event);

        verifyNoInteractions(invRepo);
        verifyNoInteractions(kafka);
    }

    @Test
    void processBookingRejectsWhenInsufficient() {
        BookingCreatedEvent event = new BookingCreatedEvent(1L, "EVT-001", "CUST-1", 5, "2026-01-01T00:00:00");

        EventInventory inventory = new EventInventory(null, "EVT-001", 10, 2, 0L);
        when(processedRepo.existsByBookingId(1L)).thenReturn(false);
        when(invRepo.findByEventId("EVT-001")).thenReturn(Optional.of(inventory));

        service.processBooking(event);

        verify(kafka).send(eq("inventory"), any(String.class), any(SeatsRejectedEvent.class));
        verify(invRepo, never()).save(any());
        verify(processedRepo).save(any(ProcessedBooking.class));
    }
}
