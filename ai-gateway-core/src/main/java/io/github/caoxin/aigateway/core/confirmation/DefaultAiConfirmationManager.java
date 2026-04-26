package io.github.caoxin.aigateway.core.confirmation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;
import io.github.caoxin.aigateway.core.policy.PolicyDecision;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class DefaultAiConfirmationManager implements AiConfirmationManager {

    private final AiConfirmationRepository repository;
    private final ObjectMapper objectMapper;

    public DefaultAiConfirmationManager(AiConfirmationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiConfirmationSnapshot create(
        AiExecutionContext context,
        AiCapability capability,
        Object command,
        PolicyDecision policy
    ) {
        Instant createdAt = Instant.now();
        String confirmationId = UUID.randomUUID().toString();
        AiConfirmationSnapshot snapshot = new AiConfirmationSnapshot(
            confirmationId,
            context.user().tenantId(),
            context.user().userId(),
            context.sessionId(),
            capability.moduleName(),
            capability.intentName(),
            toJson(command),
            capability.commandType().getName(),
            capability.riskLevel(),
            capability.permission(),
            confirmationReason(capability, policy),
            UUID.randomUUID().toString(),
            createdAt,
            createdAt.plusSeconds(capability.confirmationExpireSeconds()),
            null,
            null,
            ConfirmationStatus.PENDING
        );
        repository.save(snapshot);
        return snapshot;
    }

    @Override
    public Optional<AiConfirmationSnapshot> find(String confirmationId) {
        return repository.findById(confirmationId);
    }

    @Override
    public void save(AiConfirmationSnapshot snapshot) {
        repository.save(snapshot);
    }

    private String confirmationReason(AiCapability capability, PolicyDecision policy) {
        if (capability.confirmationMessage() != null && !capability.confirmationMessage().isBlank()) {
            return capability.confirmationMessage();
        }
        if (policy.reason() != null && !policy.reason().isBlank()) {
            return policy.reason();
        }
        return "该操作需要二次确认";
    }

    private String toJson(Object command) {
        try {
            return objectMapper.writeValueAsString(command);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化确认快照参数", exception);
        }
    }
}
