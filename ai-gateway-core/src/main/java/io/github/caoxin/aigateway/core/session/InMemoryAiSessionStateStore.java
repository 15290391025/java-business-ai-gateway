package io.github.caoxin.aigateway.core.session;

import io.github.caoxin.aigateway.core.context.AiUserContext;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAiSessionStateStore implements AiSessionStateStore {

    private final Map<String, PendingClarification> clarifications = new ConcurrentHashMap<>();

    @Override
    public Optional<PendingClarification> find(AiUserContext user, String sessionId) {
        String key = key(user, sessionId);
        PendingClarification clarification = clarifications.get(key);
        if (clarification == null) {
            return Optional.empty();
        }
        if (clarification.expired(Instant.now())) {
            clarifications.remove(key);
            return Optional.empty();
        }
        return Optional.of(clarification);
    }

    @Override
    public void save(PendingClarification clarification) {
        clarifications.put(
            key(clarification.tenantId(), clarification.userId(), clarification.sessionId()),
            clarification
        );
    }

    @Override
    public void delete(AiUserContext user, String sessionId) {
        clarifications.remove(key(user, sessionId));
    }

    private String key(AiUserContext user, String sessionId) {
        return key(user.tenantId(), user.userId(), sessionId);
    }

    private String key(String tenantId, String userId, String sessionId) {
        return tenantId + "\n" + userId + "\n" + sessionId;
    }
}
