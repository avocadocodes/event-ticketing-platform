package com.nikita.ticketing.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.ticketing.inventory.domain.EventInventory;
import com.nikita.ticketing.inventory.domain.ProcessedBooking;
import com.nikita.ticketing.inventory.domain.SeatHold;
import com.nikita.ticketing.inventory.events.BookingCreatedEvent;
import com.nikita.ticketing.inventory.outbox.OutboxEvent;
import com.nikita.ticketing.inventory.outbox.OutboxEventRepository;
import com.nikita.ticketing.inventory.repository.EventInventoryRepository;
import com.nikita.ticketing.inventory.repository.ProcessedBookingRepository;
import com.nikita.ticketing.inventory.repository.SeatHoldRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    private SeatHoldRepository seatHoldRepo;

    @Mock
    private OutboxEventRepository outboxRepo;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(invRepo, processedRepo, seatHoldRepo, outboxRepo, objectMapper);
    }

    @Test
    void processBookingCreatesHoldAndWritesSeatsReserved() {
        BookingCreatedEvent event = new BookingCreatedEvent(1L, "EVT-001", "CUST-1", 5, "2026-01-01T00:00:00");

        EventInventory inventory = new EventInventory(null, "EVT-001", 100, 100, 0L);
        when(processedRepo.existsByBookingId(1L)).thenReturn(false);
        when(invRepo.findByEventId("EVT-001")).thenReturn(Optional.of(inventory));
        when(seatHoldRepo.sumActiveHolds(any(), any())).thenReturn(0);

        service.processBooking(event);

        verify(seatHoldRepo).save(any(SeatHold.class));
        verify(processedRepo).save(any(ProcessedBooking.class));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("SeatsReserved");
    }

    @Test
    void processBookingIdempotentSkips() {
        BookingCreatedEvent event = new BookingCreatedEvent(1L, "EVT-001", "CUST-1", 5, "2026-01-01T00:00:00");

        when(processedRepo.existsByBookingId(1L)).thenReturn(true);

        service.processBooking(event);

        verifyNoInteractions(invRepo);
        verifyNoInteractions(outboxRepo);
    }

    @Test
    void processBookingRejectsWhenInsufficient() {
        BookingCreatedEvent event = new BookingCreatedEvent(1L, "EVT-001", "CUST-1", 5, "2026-01-01T00:00:00");

        EventInventory inventory = new EventInventory(null, "EVT-001", 10, 3, 0L);
        when(processedRepo.existsByBookingId(1L)).thenReturn(false);
        when(invRepo.findByEventId("EVT-001")).thenReturn(Optional.of(inventory));
        when(seatHoldRepo.sumActiveHolds(any(), any())).thenReturn(0);

        service.processBooking(event);

        verify(seatHoldRepo, never()).save(any(SeatHold.class));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("SeatsRejected");
    }

    @Test
    void handleBookingConfirmedConvertsHoldToSold() {
        SeatHold hold = new SeatHold(1L, "EVT-001", 3, LocalDateTime.now().plusMinutes(4));
        EventInventory inventory = new EventInventory(null, "EVT-001", 100, 100, 0L);

        when(seatHoldRepo.findById(1L)).thenReturn(Optional.of(hold));
        when(invRepo.findByEventId("EVT-001")).thenReturn(Optional.of(inventory));

        service.handleBookingConfirmed(1L, "EVT-001");

        ArgumentCaptor<EventInventory> invCaptor = ArgumentCaptor.forClass(EventInventory.class);
        verify(invRepo).save(invCaptor.capture());
        assertThat(invCaptor.getValue().getAvailableSeats()).isEqualTo(97);
        verify(seatHoldRepo).deleteById(1L);
    }

    @Test
    void handleBookingPaymentFailedReleasesHold() {
        SeatHold hold = new SeatHold(1L, "EVT-001", 3, LocalDateTime.now().plusMinutes(4));
        when(seatHoldRepo.findById(1L)).thenReturn(Optional.of(hold));

        service.handleBookingPaymentFailed(1L);

        verify(seatHoldRepo).deleteById(1L);
    }
}
