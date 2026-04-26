package io.github.caoxin.aigateway.core.policy;

import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;

public interface AiPolicyEngine {

    PolicyDecision evaluateBeforeInvoke(AiExecutionContext context, AiCapability capability, Object command);
}

