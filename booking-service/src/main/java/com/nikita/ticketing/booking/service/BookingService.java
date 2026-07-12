package com.nikita.ticketing.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.ticketing.booking.domain.Booking;
import com.nikita.ticketing.booking.domain.BookingStatus;
import com.nikita.ticketing.booking.domain.ProcessedEvent;
import com.nikita.ticketing.booking.dto.BookingResponse;
import com.nikita.ticketing.booking.dto.CreateBookingRequest;
import com.nikita.ticketing.booking.events.BookingConfirmedEvent;
import com.nikita.ticketing.booking.events.BookingCreatedEvent;
import com.nikita.ticketing.booking.events.BookingPaymentFailedEvent;
import com.nikita.ticketing.booking.events.SeatsRejectedEvent;
import com.nikita.ticketing.booking.events.SeatsReservedEvent;
import com.nikita.ticketing.booking.outbox.OutboxEvent;
import com.nikita.ticketing.booking.outbox.OutboxEventRepository;
import com.nikita.ticketing.booking.repository.BookingRepository;
import com.nikita.ticketing.booking.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final BigDecimal SEAT_PRICE = new BigDecimal("25.00");

    private final BookingRepository bookingRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final RestClient paymentClient;

    public BookingService(BookingRepository bookingRepository,
                          OutboxEventRepository outboxEventRepository,
                          ProcessedEventRepository processedEventRepository,
                          ObjectMapper objectMapper,
                          @Value("${payment.service.url:http://localhost:8083}") String paymentServiceUrl) {
        this.bookingRepository = bookingRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.paymentClient = RestClient.builder().baseUrl(paymentServiceUrl).build();
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest req) {
        Booking booking = new Booking(req.eventId(), req.customerId(), req.seatCount(), BookingStatus.PENDING_RESERVATION);
        booking.setAmount(SEAT_PRICE.multiply(BigDecimal.valueOf(req.seatCount())));
        Booking saved = bookingRepository.save(booking);

        BookingCreatedEvent event = new BookingCreatedEvent(
                saved.getId(), saved.getEventId(), saved.getCustomerId(),
                saved.getSeatCount(), saved.getCreatedAt().toString());
        writeOutbox("bookings", saved.getId().toString(), "BookingCreated", event);

        return BookingResponse.fromEntity(saved);
    }

    @Transactional
    public void handleSeatsReserved(SeatsReservedEvent event) {
        String eventKey = "SeatsReserved:" + event.getBookingId();
        if (processedEventRepository.existsByEventKey(eventKey)) {
            return;
        }
        processedEventRepository.save(new ProcessedEvent(eventKey));

        Booking booking = bookingRepository.findById(event.getBookingId()).orElse(null);
        if (booking == null) {
            log.warn("Booking {} not found for SeatsReserved", event.getBookingId());
            return;
        }
        booking.setStatus(BookingStatus.AWAITING_PAYMENT);
        bookingRepository.save(booking);

        Map<String, Object> paymentReq = Map.of(
                "bookingId", booking.getId(),
                "amount", booking.getAmount(),
                "currency", "USD",
                "method", "CARD"
        );

        try {
            var paymentResponse = paymentClient.post()
                    .uri("/payments")
                    .header("Idempotency-Key", "booking-" + booking.getId())
                    .header("Content-Type", "application/json")
                    .body(paymentReq)
                    .retrieve()
                    .toEntity(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = paymentResponse.getBody();
            String status = body != null ? (String) body.get("status") : null;

            if ("SUCCEEDED".equals(status)) {
                booking.setStatus(BookingStatus.CONFIRMED);
                bookingRepository.save(booking);
                writeOutbox("bookings", booking.getId().toString(), "BookingConfirmed",
                        new BookingConfirmedEvent(booking.getId(), booking.getEventId()));
            } else {
                booking.setStatus(BookingStatus.PAYMENT_FAILED);
                bookingRepository.save(booking);
                writeOutbox("bookings", booking.getId().toString(), "BookingPaymentFailed",
                        new BookingPaymentFailedEvent(booking.getId(), booking.getEventId()));
            }
        } catch (Exception e) {
            log.warn("Payment call failed for booking {}: {}", booking.getId(), e.getMessage());
            booking.setStatus(BookingStatus.PAYMENT_FAILED);
            bookingRepository.save(booking);
            writeOutbox("bookings", booking.getId().toString(), "BookingPaymentFailed",
                    new BookingPaymentFailedEvent(booking.getId(), booking.getEventId()));
        }
    }

    @Transactional
    public void handleSeatsRejected(SeatsRejectedEvent event) {
        String eventKey = "SeatsRejected:" + event.getBookingId();
        if (processedEventRepository.existsByEventKey(eventKey)) {
            return;
        }
        processedEventRepository.save(new ProcessedEvent(eventKey));

        bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
            booking.setStatus(BookingStatus.REJECTED);
            bookingRepository.save(booking);
        });
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
