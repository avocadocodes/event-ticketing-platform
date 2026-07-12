package com.nikita.ticketing.payment.service;

import com.nikita.ticketing.payment.domain.Payment;
import com.nikita.ticketing.payment.domain.PaymentStatus;
import com.nikita.ticketing.payment.dto.CreatePaymentRequest;
import com.nikita.ticketing.payment.dto.PaymentResponse;
import com.nikita.ticketing.payment.gateway.GatewayResult;
import com.nikita.ticketing.payment.gateway.SimulatedGatewayClient;
import com.nikita.ticketing.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps;

    @Mock
    private SimulatedGatewayClient gatewayClient;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void idempotencyHitReturnsExistingPayment() {
        UUID existingId = UUID.randomUUID();
        String idempotencyKey = "key-123";

        Payment savedPayment = new Payment();
        savedPayment.setId(existingId);
        savedPayment.setBookingId(1L);
        savedPayment.setAmount(new BigDecimal("25.00"));
        savedPayment.setCurrency("USD");
        savedPayment.setMethod("CARD");
        savedPayment.setStatus(PaymentStatus.SUCCEEDED);
        savedPayment.setCreatedAt(LocalDateTime.now());
        savedPayment.setUpdatedAt(LocalDateTime.now());

        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(existingId.toString());
        when(paymentRepository.findById(existingId)).thenReturn(Optional.of(savedPayment));

        CreatePaymentRequest req = new CreatePaymentRequest(1L, new BigDecimal("25.00"), "USD", "CARD");
        PaymentResponse response = paymentService.processPayment(req, idempotencyKey);

        assertThat(response.id()).isEqualTo(existingId);
        verify(gatewayClient, never()).charge(any(), any());
    }

    @Test
    void successFlowSavesSucceeded() {
        String idempotencyKey = "key-success";
        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(null);
        when(gatewayClient.charge(any(), any())).thenReturn(new GatewayResult(true, "REF-123"));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.save(captor.capture())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
                p.setCreatedAt(LocalDateTime.now());
                p.setUpdatedAt(LocalDateTime.now());
            }
            return p;
        });

        CreatePaymentRequest req = new CreatePaymentRequest(2L, new BigDecimal("50.00"), "USD", "CARD");
        PaymentResponse response = paymentService.processPayment(req, idempotencyKey);

        verify(paymentRepository, times(2)).save(any(Payment.class));
        Payment secondSave = captor.getAllValues().get(1);
        assertThat(secondSave.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(secondSave.getGatewayReference()).isEqualTo("REF-123");
        verify(valueOps).set(eq("idempotency:" + idempotencyKey), any(String.class), eq(24L), eq(TimeUnit.HOURS));
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void failureFlowSavesFailed() {
        String idempotencyKey = "key-fail";
        when(valueOps.get("idempotency:" + idempotencyKey)).thenReturn(null);
        when(gatewayClient.charge(any(), any())).thenReturn(new GatewayResult(false, null));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.save(captor.capture())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
                p.setCreatedAt(LocalDateTime.now());
                p.setUpdatedAt(LocalDateTime.now());
            }
            return p;
        });

        CreatePaymentRequest req = new CreatePaymentRequest(3L, new BigDecimal("10.00"), "USD", "ACH");
        PaymentResponse response = paymentService.processPayment(req, idempotencyKey);

        verify(paymentRepository, times(2)).save(any(Payment.class));
        Payment secondSave = captor.getAllValues().get(1);
        assertThat(secondSave.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
    }
}
