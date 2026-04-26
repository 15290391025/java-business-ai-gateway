package io.github.caoxin.aigateway.core.confirmation;

import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;
import io.github.caoxin.aigateway.core.policy.PolicyDecision;

import java.util.Optional;

public interface AiConfirmationManager {

    AiConfirmationSnapshot create(
        AiExecutionContext context,
        AiCapability capability,
        Object command,
        PolicyDecision policy
    );

    Optional<AiConfirmationSnapshot> find(String confirmationId);

    void save(AiConfirmationSnapshot snapshot);
}

