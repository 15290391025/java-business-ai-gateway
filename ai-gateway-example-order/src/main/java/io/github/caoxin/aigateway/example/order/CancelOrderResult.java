package io.github.caoxin.aigateway.example.order;

public record CancelOrderResult(
    boolean success,
    String message,
    String orderId,
    String previousStatus,
    String currentStatus
) {
}

