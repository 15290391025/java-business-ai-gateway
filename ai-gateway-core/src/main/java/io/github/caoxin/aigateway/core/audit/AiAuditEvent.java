package io.github.caoxin.aigateway.core.audit;

import io.github.caoxin.aigateway.annotation.RiskLevel;

import java.time.Instant;

public record AiAuditEvent(
    String id,
    String tenantId,
    String userId,
    String sessionId,
    String userInput,
    String moduleName,
    String intentName,
    String commandJson,
    RiskLevel riskLevel,
    String permission,
    String confirmationId,
    String status,
    String resultSummary,
    String errorMessage,
    Instant createdAt
) {
}

