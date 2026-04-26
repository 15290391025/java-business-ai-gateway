package io.github.caoxin.aigateway.core.policy;

import io.github.caoxin.aigateway.annotation.RiskLevel;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;

public class DefaultAiPolicyEngine implements AiPolicyEngine {

    @Override
    public PolicyDecision evaluateBeforeInvoke(AiExecutionContext context, AiCapability capability, Object command) {
        if (capability.riskLevel().ordinal() >= RiskLevel.HIGH.ordinal()) {
            return PolicyDecision.confirmationRequired("高风险操作需要二次确认");
        }
        return PolicyDecision.allowed();
    }
}
