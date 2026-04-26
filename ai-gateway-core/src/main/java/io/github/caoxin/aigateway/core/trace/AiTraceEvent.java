package io.github.caoxin.aigateway.core.trace;

import java.time.Instant;

public record AiTraceEvent(
    String id,
    String tenantId,
    String userId,
    String sessionId,
    String phase,
    String moduleName,
    String intentName,
    String status,
    String modelProvider,
    String modelName,
    Long inputTokens,
    Long outputTokens,
    Long latencyMs,
    Double routeConfidence,
    String metadataJson,
    Instant createdAt
) {
}
