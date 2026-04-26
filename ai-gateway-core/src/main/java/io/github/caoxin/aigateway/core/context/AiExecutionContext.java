package io.github.caoxin.aigateway.core.context;

import io.github.caoxin.aigateway.core.gateway.AiChatRequest;

import java.time.Instant;

public record AiExecutionContext(
    String sessionId,
    String userInput,
    AiUserContext user,
    Instant createdAt
) {

    public static AiExecutionContext from(AiChatRequest request, AiUserContext user) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
            ? "default-session"
            : request.sessionId();
        return new AiExecutionContext(sessionId, request.userInput(), user, Instant.now());
    }
}

