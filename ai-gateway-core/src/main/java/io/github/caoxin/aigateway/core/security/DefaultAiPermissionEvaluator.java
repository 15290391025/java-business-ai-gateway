package io.github.caoxin.aigateway.core.security;

import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiUserContext;

public class DefaultAiPermissionEvaluator implements AiPermissionEvaluator {

    @Override
    public PermissionDecision check(AiUserContext user, AiCapability capability, Object command) {
        if (user.hasPermission(capability.permission())) {
            return PermissionDecision.allowed();
        }
        return PermissionDecision.denied("缺少权限: " + capability.permission());
    }
}

