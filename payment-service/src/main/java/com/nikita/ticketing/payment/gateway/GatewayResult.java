package com.nikita.ticketing.payment.gateway;

public record GatewayResult(boolean success, String reference) {
}
