package com.nikita.ticketing.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.ticketing.booking.domain.Booking;
import com.nikita.ticketing.booking.domain.BookingStatus;
import com.nikita.ticketing.booking.dto.BookingResponse;
import com.nikita.ticketing.booking.dto.CreateBookingRequest;
import com.nikita.ticketing.booking.events.SeatsRejectedEvent;
import com.nikita.ticketing.booking.outbox.OutboxEvent;
import com.nikita.ticketing.booking.outbox.OutboxEventRepository;
import com.nikita.ticketing.booking.repository.BookingRepository;
import com.nikita.ticketing.booking.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        // Use invalid URL so any accidental payment call fails immediately
        bookingService = new BookingService(
                bookingRepository, outboxEventRepository, processedEventRepository,
                objectMapper, "http://localhost:0");
    }

    private Booking buildSavedBooking() {
        Booking saved = new Booking("EVT-1", "CUST-1", 2, BookingStatus.PENDING_RESERVATION);
        saved.setId(1L);
        saved.setAmount(new BigDecimal("50.00"));
        saved.setCreatedAt(LocalDateTime.now());
        saved.setUpdatedAt(LocalDateTime.now());
        return saved;
    }

    @Test
    void createBookingSavesAndWritesOutbox() {
        Booking saved = buildSavedBooking();
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        CreateBookingRequest req = new CreateBookingRequest("EVT-1", "CUST-1", 2);
        BookingResponse response = bookingService.createBooking(req);

        verify(bookingRepository).save(any(Booking.class));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("BookingCreated");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("bookings");
        assertThat(response.id()).isEqualTo(1L);
    }

    @Test
    void handleSeatsRejectedSetsRejectedStatus() {
        when(processedEventRepository.existsByEventKey("SeatsRejected:1")).thenReturn(false);
        Booking booking = buildSavedBooking();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.handleSeatsRejected(new SeatsRejectedEvent(1L, "EVT-1", "Insufficient seats"));

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BookingStatus.REJECTED);
    }

    @Test
    void handleSeatsRejectedIdempotentSkips() {
        when(processedEventRepository.existsByEventKey("SeatsRejected:1")).thenReturn(true);

        bookingService.handleSeatsRejected(new SeatsRejectedEvent(1L, "EVT-1", "Insufficient seats"));

        verify(bookingRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void getBookingThrowsWhenMissing() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBooking(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }
}
