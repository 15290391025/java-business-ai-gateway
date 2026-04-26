package io.github.caoxin.aigateway.core.confirmation;

import io.github.caoxin.aigateway.annotation.RiskLevel;

import java.time.Instant;

public record AiConfirmationSnapshot(
    String confirmationId,
    String tenantId,
    String userId,
    String sessionId,
    String moduleName,
    String intentName,
    String commandJson,
    String commandClassName,
    RiskLevel riskLevel,
    String permission,
    String reason,
    String idempotencyKey,
    Instant createdAt,
    Instant expiresAt,
    Instant confirmedAt,
    Instant executedAt,
    ConfirmationStatus status
) {

    public AiConfirmationSnapshot withStatus(ConfirmationStatus nextStatus, Instant confirmedAt, Instant executedAt) {
        return new AiConfirmationSnapshot(
            confirmationId,
            tenantId,
            userId,
            sessionId,
            moduleName,
            intentName,
            commandJson,
            commandClassName,
            riskLevel,
            permission,
            reason,
            idempotencyKey,
            createdAt,
            expiresAt,
            confirmedAt,
            executedAt,
            nextStatus
        );
    }
}

