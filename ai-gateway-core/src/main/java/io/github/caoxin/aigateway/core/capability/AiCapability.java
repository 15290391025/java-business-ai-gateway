package io.github.caoxin.aigateway.core.capability;

import io.github.caoxin.aigateway.annotation.RiskLevel;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public record AiCapability(
    String moduleName,
    String moduleDescription,
    String intentName,
    String intentDescription,
    Class<?> commandType,
    Class<?> resultType,
    RiskLevel riskLevel,
    boolean requiresConfirmation,
    long confirmationExpireSeconds,
    String confirmationMessage,
    String permission,
    Object bean,
    Method method,
    List<String> examples,
    Map<String, Object> metadata
) {

    public String id() {
        return moduleName + "." + intentName;
    }
}

