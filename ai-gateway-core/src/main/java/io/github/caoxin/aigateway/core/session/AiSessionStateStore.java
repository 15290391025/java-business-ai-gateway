package io.github.caoxin.aigateway.core.session;

import io.github.caoxin.aigateway.core.context.AiUserContext;

import java.util.Optional;

public interface AiSessionStateStore {

    Optional<PendingClarification> find(AiUserContext user, String sessionId);

    void save(PendingClarification clarification);

    void delete(AiUserContext user, String sessionId);
}
