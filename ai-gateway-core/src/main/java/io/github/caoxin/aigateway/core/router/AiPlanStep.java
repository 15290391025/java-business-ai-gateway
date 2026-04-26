package io.github.caoxin.aigateway.core.router;

import io.github.caoxin.aigateway.annotation.RiskLevel;

import java.util.Map;

public record AiPlanStep(
    String stepId,
    String moduleName,
    String intentName,
    Map<String, Object> arguments,
    String conditionExpression,
    RiskLevel riskLevel,
    boolean requiresConfirmation
) {
}

