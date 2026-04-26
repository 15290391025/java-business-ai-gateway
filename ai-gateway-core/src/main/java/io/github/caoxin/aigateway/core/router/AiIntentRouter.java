package io.github.caoxin.aigateway.core.router;

import io.github.caoxin.aigateway.core.context.AiExecutionContext;

public interface AiIntentRouter {

    AiRoutePlan route(AiExecutionContext context);
}

