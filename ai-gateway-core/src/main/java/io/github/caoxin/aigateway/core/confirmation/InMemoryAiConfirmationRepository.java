package io.github.caoxin.aigateway.core.confirmation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAiConfirmationRepository implements AiConfirmationRepository {

    private final Map<String, AiConfirmationSnapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public void save(AiConfirmationSnapshot snapshot) {
        snapshots.put(snapshot.confirmationId(), snapshot);
    }

    @Override
    public Optional<AiConfirmationSnapshot> findById(String confirmationId) {
        return Optional.ofNullable(snapshots.get(confirmationId));
    }
}

