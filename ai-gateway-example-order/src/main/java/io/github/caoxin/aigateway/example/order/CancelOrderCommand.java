package io.github.caoxin.aigateway.example.order;

import jakarta.validation.constraints.NotBlank;

public record CancelOrderCommand(
    @NotBlank String orderId,
    @NotBlank String reason
) {
}

