package com.nikita.ticketing.payment.service;

import com.nikita.ticketing.payment.domain.Payment;
import com.nikita.ticketing.payment.domain.PaymentStatus;
import com.nikita.ticketing.payment.dto.CreatePaymentRequest;
import com.nikita.ticketing.payment.dto.PaymentResponse;
import com.nikita.ticketing.payment.gateway.GatewayResult;
import com.nikita.ticketing.payment.gateway.SimulatedGatewayClient;
import com.nikita.ticketing.payment.repository.PaymentRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StringRedisTemplate redisTemplate;
    private final SimulatedGatewayClient gatewayClient;

    public PaymentService(PaymentRepository paymentRepository,
                          StringRedisTemplate redisTemplate,
                          SimulatedGatewayClient gatewayClient) {
        this.paymentRepository = paymentRepository;
        this.redisTemplate = redisTemplate;
        this.gatewayClient = gatewayClient;
    }

    @Transactional
    public PaymentResponse processPayment(CreatePaymentRequest req, String idempotencyKey) {
        ValueOperations<String, String> ops = redisTemplate.opsForValue();
        String redisKey = "idempotency:" + idempotencyKey;
        String existing = ops.get(redisKey);
        if (existing != null) {
            UUID existingId = UUID.fromString(existing);
            Payment existingPayment = paymentRepository.findById(existingId)
                    .orElseThrow(() -> new NoSuchElementException("Payment not found: " + existingId));
            return PaymentResponse.fromEntity(existingPayment);
        }

        Payment payment = new Payment();
        payment.setBookingId(req.bookingId());
        payment.setAmount(req.amount());
        payment.setCurrency(req.currency());
        payment.setMethod(req.method());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        GatewayResult result = gatewayClient.charge(req.amount(), req.method());

        payment.setStatus(result.success() ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);
        payment.setGatewayReference(result.reference());
        payment = paymentRepository.save(payment);

        ops.set(redisKey, payment.getId().toString(), 24, TimeUnit.HOURS);

        return PaymentResponse.fromEntity(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + id));
        return PaymentResponse.fromEntity(payment);
    }
}
