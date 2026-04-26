package io.github.caoxin.aigateway.example.order;

public record OrderDetailResult(
    String orderId,
    String status,
    String paymentStatus,
    String logisticsStatus,
    String address
) {
}

