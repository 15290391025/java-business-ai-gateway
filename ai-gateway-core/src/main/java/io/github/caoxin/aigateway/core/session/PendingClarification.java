package io.github.caoxin.aigateway.core.session;

import io.github.caoxin.aigateway.core.gateway.AiStepExecutionResult;
import io.github.caoxin.aigateway.core.router.AiRoutePlan;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PendingClarification(
    String tenantId,
    String userId,
    String sessionId,
    String originalUserInput,
    AiRoutePlan plan,
    int stepIndex,
    Map<String, Object> arguments,
    List<String> missingFields,
    List<AiStepExecutionResult> priorResults,
    Object previousResult,
    Instant createdAt,
    Instant expiresAt
) {

    public PendingClarification {
        arguments = arguments == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        priorResults = priorResults == null ? List.of() : List.copyOf(priorResults);
    }

    public boolean expired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}
