package io.github.caoxin.aigateway.core.gateway;

public record AiChatRequest(
    String sessionId,
    String userInput
) {
}

