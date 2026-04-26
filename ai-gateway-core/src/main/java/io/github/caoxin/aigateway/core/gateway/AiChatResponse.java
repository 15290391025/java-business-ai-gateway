package io.github.caoxin.aigateway.core.gateway;

import io.github.caoxin.aigateway.annotation.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiChatResponse(
    String type,
    String message,
    String confirmationId,
    String moduleName,
    String intentName,
    RiskLevel riskLevel,
    Map<String, Object> argumentsPreview,
    Instant expiresAt,
    Object result,
    List<String> missingFields
) {

    public static AiChatResponse actionResult(String message, String moduleName, String intentName, Object result) {
        return new AiChatResponse("action_result", message, null, moduleName, intentName, null, Map.of(), null, result, List.of());
    }

    public static AiChatResponse confirmationRequired(
        String message,
        String confirmationId,
        String moduleName,
        String intentName,
        RiskLevel riskLevel,
        Map<String, Object> argumentsPreview,
        Instant expiresAt
    ) {
        return new AiChatResponse(
            "confirmation_required",
            message,
            confirmationId,
            moduleName,
            intentName,
            riskLevel,
            argumentsPreview,
            expiresAt,
            null,
            List.of()
        );
    }

    public static AiChatResponse clarification(String message, List<String> missingFields) {
        return new AiChatResponse("clarification_required", message, null, null, null, null, Map.of(), null, null, missingFields);
    }

    public static AiChatResponse permissionDenied(String message) {
        return new AiChatResponse("permission_denied", message, null, null, null, null, Map.of(), null, null, List.of());
    }

    public static AiChatResponse denied(String message) {
        return new AiChatResponse("denied", message, null, null, null, null, Map.of(), null, null, List.of());
    }

    public static AiChatResponse error(String message) {
        return new AiChatResponse("error", message, null, null, null, null, Map.of(), null, null, List.of());
    }
}

