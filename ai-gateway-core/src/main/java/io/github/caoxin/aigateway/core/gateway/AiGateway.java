package io.github.caoxin.aigateway.core.gateway;

import io.github.caoxin.aigateway.core.context.AiUserContext;

public interface AiGateway {

    AiChatResponse chat(AiChatRequest request, AiUserContext userContext);

    AiConfirmResponse confirm(AiConfirmRequest request, AiUserContext userContext);
}
