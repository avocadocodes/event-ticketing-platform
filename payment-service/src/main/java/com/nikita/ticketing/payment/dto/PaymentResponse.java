package com.nikita.ticketing.payment.dto;

import com.nikita.ticketing.payment.domain.Payment;
import com.nikita.ticketing.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        Long bookingId,
        BigDecimal amount,
        String currency,
        String method,
        PaymentStatus status,
        String gatewayReference,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaymentResponse fromEntity(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getGatewayReference(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
