package io.github.caoxin.aigateway.core.capability;

import io.github.caoxin.aigateway.annotation.RiskLevel;

public record AiCapabilityDescriptor(
    String moduleName,
    String intentName,
    String description,
    String permission,
    RiskLevel riskLevel,
    boolean requiresConfirmation
) {
    public static AiCapabilityDescriptor from(AiCapability capability) {
        return new AiCapabilityDescriptor(
            capability.moduleName(),
            capability.intentName(),
            capability.intentDescription(),
            capability.permission(),
            capability.riskLevel(),
            capability.requiresConfirmation()
        );
    }
}

