package com.nikita.ticketing.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nikita.ticketing.payment.dto.CreatePaymentRequest;
import com.nikita.ticketing.payment.dto.PaymentResponse;
import com.nikita.ticketing.payment.domain.PaymentStatus;
import com.nikita.ticketing.payment.exception.GlobalExceptionHandler;
import com.nikita.ticketing.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@ActiveProfiles("test")
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        CreatePaymentRequest req = new CreatePaymentRequest(1L, new BigDecimal("25.00"), "USD", "CARD");

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validRequestReturns201() throws Exception {
        UUID paymentId = UUID.randomUUID();
        PaymentResponse mockResponse = new PaymentResponse(
                paymentId, 1L, new BigDecimal("25.00"), "USD", "CARD",
                PaymentStatus.SUCCEEDED, "REF-ABC", LocalDateTime.now(), LocalDateTime.now());

        when(paymentService.processPayment(any(CreatePaymentRequest.class), eq("idem-key-1")))
                .thenReturn(mockResponse);

        CreatePaymentRequest req = new CreatePaymentRequest(1L, new BigDecimal("25.00"), "USD", "CARD");

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "idem-key-1")
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()));
    }
}
