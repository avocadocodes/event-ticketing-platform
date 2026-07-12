package com.nikita.ticketing.payment.gateway;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class SimulatedGatewayClient {

    @Retry(name = "gateway")
    @CircuitBreaker(name = "gateway", fallbackMethod = "chargeFallback")
    public GatewayResult charge(BigDecimal amount, String method) {
        if (Math.random() < 0.3) {
            throw new RuntimeException("Simulated gateway failure");
        }
        return new GatewayResult(true, "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    public GatewayResult chargeFallback(BigDecimal amount, String method, Throwable t) {
        return new GatewayResult(false, null);
    }
}
