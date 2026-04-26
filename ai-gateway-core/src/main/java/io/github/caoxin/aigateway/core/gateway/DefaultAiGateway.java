package io.github.caoxin.aigateway.core.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.core.audit.AiAuditEvent;
import io.github.caoxin.aigateway.core.audit.AiAuditLogger;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationManager;
import io.github.caoxin.aigateway.core.confirmation.AiConfirmationSnapshot;
import io.github.caoxin.aigateway.core.confirmation.ConfirmationStatus;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;
import io.github.caoxin.aigateway.core.context.AiUserContext;
import io.github.caoxin.aigateway.core.invoke.AiCapabilityInvoker;
import io.github.caoxin.aigateway.core.invoke.AiInvokeResult;
import io.github.caoxin.aigateway.core.invoke.ArgumentBinder;
import io.github.caoxin.aigateway.core.policy.AiPolicyEngine;
import io.github.caoxin.aigateway.core.policy.PolicyDecision;
import io.github.caoxin.aigateway.core.router.AiIntentRouter;
import io.github.caoxin.aigateway.core.router.AiPlanStep;
import io.github.caoxin.aigateway.core.router.AiRoutePlan;
import io.github.caoxin.aigateway.core.security.AiPermissionEvaluator;
import io.github.caoxin.aigateway.core.security.PermissionDecision;
import io.github.caoxin.aigateway.core.validation.AiCommandValidator;
import io.github.caoxin.aigateway.core.validation.ValidationResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class DefaultAiGateway implements AiGateway {

    private final AiCapabilityRegistry registry;
    private final AiIntentRouter router;
    private final ArgumentBinder argumentBinder;
    private final AiCommandValidator commandValidator;
    private final AiPermissionEvaluator permissionEvaluator;
    private final AiPolicyEngine policyEngine;
    private final AiConfirmationManager confirmationManager;
    private final AiCapabilityInvoker invoker;
    private final AiAuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    public DefaultAiGateway(
        AiCapabilityRegistry registry,
        AiIntentRouter router,
        ArgumentBinder argumentBinder,
        AiCommandValidator commandValidator,
        AiPermissionEvaluator permissionEvaluator,
        AiPolicyEngine policyEngine,
        AiConfirmationManager confirmationManager,
        AiCapabilityInvoker invoker,
        AiAuditLogger auditLogger,
        ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.router = router;
        this.argumentBinder = argumentBinder;
        this.commandValidator = commandValidator;
        this.permissionEvaluator = permissionEvaluator;
        this.policyEngine = policyEngine;
        this.confirmationManager = confirmationManager;
        this.invoker = invoker;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, AiUserContext userContext) {
        AiExecutionContext context = AiExecutionContext.from(request, userContext);
        AiRoutePlan plan = router.route(context);

        if (plan.requiresClarification()) {
            return AiChatResponse.clarification(plan.clarificationQuestion(), java.util.List.of());
        }

        if (plan.steps().isEmpty()) {
            return AiChatResponse.clarification("没有生成可执行步骤", java.util.List.of());
        }

        AiPlanStep step = plan.steps().get(0);
        Optional<AiCapability> capabilityOptional = registry.find(step.moduleName(), step.intentName());
        if (capabilityOptional.isEmpty()) {
            return AiChatResponse.error("能力不存在: " + step.moduleName() + "." + step.intentName());
        }

        AiCapability capability = capabilityOptional.get();
        Object command = argumentBinder.bind(step.arguments(), capability.commandType());

        ValidationResult validation = commandValidator.validate(command);
        if (!validation.valid()) {
            return AiChatResponse.clarification(validation.message(), validation.missingFields());
        }

        PermissionDecision permission = permissionEvaluator.check(context.user(), capability, command);
        if (!permission.allowed()) {
            audit(context, capability, command, null, "PERMISSION_DENIED", null, permission.reason());
            return AiChatResponse.permissionDenied(permission.reason());
        }

        PolicyDecision policy = policyEngine.evaluateBeforeInvoke(context, capability, command);
        if (policy.denied()) {
            audit(context, capability, command, null, "DENIED", null, policy.reason());
            return AiChatResponse.denied(policy.reason());
        }

        if (capability.requiresConfirmation() || policy.requiresConfirmation()) {
            AiConfirmationSnapshot snapshot = confirmationManager.create(context, capability, command, policy);
            audit(context, capability, command, snapshot.confirmationId(), "CONFIRMATION_REQUIRED", null, snapshot.reason());
            return AiChatResponse.confirmationRequired(
                snapshot.reason(),
                snapshot.confirmationId(),
                capability.moduleName(),
                capability.intentName(),
                capability.riskLevel(),
                argumentBinder.preview(command),
                snapshot.expiresAt()
            );
        }

        AiInvokeResult invokeResult = invoker.invoke(capability, command, context);
        if (!invokeResult.success()) {
            audit(context, capability, command, null, "FAILED", null, invokeResult.errorMessage());
            return AiChatResponse.error(invokeResult.errorMessage());
        }

        audit(context, capability, command, null, "SUCCESS", summarize(invokeResult.result()), null);
        return AiChatResponse.actionResult("操作已完成", capability.moduleName(), capability.intentName(), invokeResult.result());
    }

    @Override
    public AiConfirmResponse confirm(AiConfirmRequest request, AiUserContext userContext) {
        Optional<AiConfirmationSnapshot> snapshotOptional = confirmationManager.find(request.confirmationId());
        if (snapshotOptional.isEmpty()) {
            return AiConfirmResponse.notFound(request.confirmationId());
        }

        AiConfirmationSnapshot snapshot = snapshotOptional.get();
        if (!snapshot.tenantId().equals(userContext.tenantId()) || !snapshot.userId().equals(userContext.userId())) {
            return AiConfirmResponse.denied(snapshot.confirmationId(), "确认记录不属于当前用户或租户");
        }
        if (snapshot.status() == ConfirmationStatus.EXECUTED) {
            return AiConfirmResponse.alreadyExecuted(snapshot.confirmationId());
        }
        if (snapshot.expiresAt().isBefore(Instant.now())) {
            confirmationManager.save(snapshot.withStatus(ConfirmationStatus.EXPIRED, snapshot.confirmedAt(), snapshot.executedAt()));
            return AiConfirmResponse.expired(snapshot.confirmationId());
        }
        if (snapshot.status() != ConfirmationStatus.PENDING) {
            return AiConfirmResponse.denied(snapshot.confirmationId(), "确认记录状态不可执行: " + snapshot.status());
        }

        Optional<AiCapability> capabilityOptional = registry.find(snapshot.moduleName(), snapshot.intentName());
        if (capabilityOptional.isEmpty()) {
            return AiConfirmResponse.denied(snapshot.confirmationId(), "能力不存在");
        }

        AiCapability capability = capabilityOptional.get();
        Object command = readCommand(snapshot, capability.commandType());
        AiExecutionContext context = new AiExecutionContext(
            snapshot.sessionId(),
            "CONFIRMED_ACTION",
            userContext,
            Instant.now()
        );

        PermissionDecision permission = permissionEvaluator.check(userContext, capability, command);
        if (!permission.allowed()) {
            confirmationManager.save(snapshot.withStatus(ConfirmationStatus.DENIED, Instant.now(), null));
            audit(context, capability, command, snapshot.confirmationId(), "PERMISSION_DENIED", null, permission.reason());
            return AiConfirmResponse.denied(snapshot.confirmationId(), permission.reason());
        }

        ValidationResult validation = commandValidator.validate(command);
        if (!validation.valid()) {
            confirmationManager.save(snapshot.withStatus(ConfirmationStatus.DENIED, Instant.now(), null));
            audit(context, capability, command, snapshot.confirmationId(), "VALIDATION_FAILED", null, validation.message());
            return AiConfirmResponse.denied(snapshot.confirmationId(), validation.message());
        }

        AiInvokeResult invokeResult = invoker.invoke(capability, command, context);
        Instant executedAt = Instant.now();
        confirmationManager.save(snapshot.withStatus(ConfirmationStatus.EXECUTED, executedAt, executedAt));

        if (!invokeResult.success()) {
            audit(context, capability, command, snapshot.confirmationId(), "FAILED", null, invokeResult.errorMessage());
            return AiConfirmResponse.denied(snapshot.confirmationId(), invokeResult.errorMessage());
        }

        audit(context, capability, command, snapshot.confirmationId(), "SUCCESS", summarize(invokeResult.result()), null);
        return AiConfirmResponse.executed(snapshot.confirmationId(), invokeResult.result());
    }

    private Object readCommand(AiConfirmationSnapshot snapshot, Class<?> commandType) {
        try {
            return objectMapper.readValue(snapshot.commandJson(), commandType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取确认快照参数", exception);
        }
    }

    private void audit(
        AiExecutionContext context,
        AiCapability capability,
        Object command,
        String confirmationId,
        String status,
        String resultSummary,
        String errorMessage
    ) {
        auditLogger.record(new AiAuditEvent(
            UUID.randomUUID().toString(),
            context.user().tenantId(),
            context.user().userId(),
            context.sessionId(),
            context.userInput(),
            capability.moduleName(),
            capability.intentName(),
            toJson(command),
            capability.riskLevel(),
            capability.permission(),
            confirmationId,
            status,
            resultSummary,
            errorMessage,
            Instant.now()
        ));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String summarize(Object value) {
        String json = toJson(value);
        if (json.length() <= 512) {
            return json;
        }
        return json.substring(0, 512);
    }
}
