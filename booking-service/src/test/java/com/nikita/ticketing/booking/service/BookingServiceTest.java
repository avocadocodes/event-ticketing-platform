package com.nikita.ticketing.booking.service;

import com.nikita.ticketing.booking.domain.Booking;
import com.nikita.ticketing.booking.domain.BookingStatus;
import com.nikita.ticketing.booking.dto.BookingResponse;
import com.nikita.ticketing.booking.dto.CreateBookingRequest;
import com.nikita.ticketing.booking.events.BookingCreatedEvent;
import com.nikita.ticketing.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate;

    @InjectMocks
    private BookingService bookingService;

    private Booking buildSavedBooking() {
        Booking saved = new Booking("EVT-1", "CUST-1", 2, BookingStatus.RESERVED);
        saved.setId(1L);
        saved.setCreatedAt(LocalDateTime.now());
        saved.setUpdatedAt(LocalDateTime.now());
        return saved;
    }

    @Test
    void createBookingSavesAndPublishes() {
        Booking saved = buildSavedBooking();
        when(bookingRepository.save(any(Booking.class))).thenReturn(saved);

        CreateBookingRequest req = new CreateBookingRequest("EVT-1", "CUST-1", 2);
        BookingResponse response = bookingService.createBooking(req);

        verify(bookingRepository).save(any(Booking.class));

        ArgumentCaptor<BookingCreatedEvent> eventCaptor = ArgumentCaptor.forClass(BookingCreatedEvent.class);
        verify(kafkaTemplate).send(eq("bookings"), eq("1"), eventCaptor.capture());

        assertThat(response.id()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getBookingId()).isEqualTo(1L);
    }

    @Test
    void getBookingReturns() {
        Booking saved = buildSavedBooking();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(saved));

        BookingResponse response = bookingService.getBooking(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.eventId()).isEqualTo("EVT-1");
        assertThat(response.customerId()).isEqualTo("CUST-1");
        assertThat(response.seatCount()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("RESERVED");
    }

    @Test
    void getBookingThrowsWhenMissing() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBooking(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }
}
