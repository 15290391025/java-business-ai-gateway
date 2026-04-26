package io.github.caoxin.aigateway.core.security;

import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiUserContext;

public interface AiPermissionEvaluator {

    PermissionDecision check(AiUserContext user, AiCapability capability, Object command);
}

