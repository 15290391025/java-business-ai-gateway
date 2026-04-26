package io.github.caoxin.aigateway.core.invoke;

import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;

public interface AiCapabilityInvoker {

    AiInvokeResult invoke(AiCapability capability, Object command, AiExecutionContext context);
}

