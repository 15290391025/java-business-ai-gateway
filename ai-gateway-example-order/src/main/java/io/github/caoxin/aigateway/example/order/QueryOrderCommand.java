package io.github.caoxin.aigateway.example.order;

import jakarta.validation.constraints.NotBlank;

public record QueryOrderCommand(
    @NotBlank String orderId
) {
}

