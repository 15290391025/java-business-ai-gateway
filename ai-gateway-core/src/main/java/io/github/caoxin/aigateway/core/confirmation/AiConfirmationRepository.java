package io.github.caoxin.aigateway.core.confirmation;

import java.util.Optional;

public interface AiConfirmationRepository {

    void save(AiConfirmationSnapshot snapshot);

    Optional<AiConfirmationSnapshot> findById(String confirmationId);
}

