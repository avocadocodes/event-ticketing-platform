package com.nikita.ticketing.booking.service;

import com.nikita.ticketing.booking.domain.Booking;
import com.nikita.ticketing.booking.domain.BookingStatus;
import com.nikita.ticketing.booking.dto.BookingResponse;
import com.nikita.ticketing.booking.dto.CreateBookingRequest;
import com.nikita.ticketing.booking.events.BookingCreatedEvent;
import com.nikita.ticketing.booking.repository.BookingRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class BookingService {

    static final String TOPIC = "bookings";

    private final BookingRepository bookingRepository;
    private final KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate;

    public BookingService(BookingRepository bookingRepository,
                          KafkaTemplate<String, BookingCreatedEvent> kafkaTemplate) {
        this.bookingRepository = bookingRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public BookingResponse createBooking(CreateBookingRequest req) {
        Booking booking = new Booking(req.eventId(), req.customerId(), req.seatCount(), BookingStatus.RESERVED);
        Booking saved = bookingRepository.save(booking);

        BookingCreatedEvent event = new BookingCreatedEvent(
                saved.getId(),
                saved.getEventId(),
                saved.getCustomerId(),
                saved.getSeatCount(),
                saved.getCreatedAt().toString()
        );
        kafkaTemplate.send(TOPIC, saved.getId().toString(), event);

        return BookingResponse.fromEntity(saved);
    }

    public BookingResponse getBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Booking not found: " + id));
        return BookingResponse.fromEntity(booking);
    }

    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll().stream()
                .map(BookingResponse::fromEntity)
                .toList();
    }
}
