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
import io.github.caoxin.aigateway.core.trace.AiTraceEvent;
import io.github.caoxin.aigateway.core.trace.AiTraceLogger;
import io.github.caoxin.aigateway.core.validation.AiCommandValidator;
import io.github.caoxin.aigateway.core.validation.ValidationResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultAiGateway implements AiGateway {

    private static final Pattern CONDITION_PATTERN = Pattern.compile(
        "previous\\.([A-Za-z0-9_.]+)\\s*(==|!=)\\s*('([^']*)'|\"([^\"]*)\"|[A-Za-z0-9_.-]+)"
    );

    private final AiCapabilityRegistry registry;
    private final AiIntentRouter router;
    private final ArgumentBinder argumentBinder;
    private final AiCommandValidator commandValidator;
    private final AiPermissionEvaluator permissionEvaluator;
    private final AiPolicyEngine policyEngine;
    private final AiConfirmationManager confirmationManager;
    private final AiCapabilityInvoker invoker;
    private final AiAuditLogger auditLogger;
    private final AiTraceLogger traceLogger;
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
        AiTraceLogger traceLogger,
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
        this.traceLogger = traceLogger;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, AiUserContext userContext) {
        AiExecutionContext context = AiExecutionContext.from(request, userContext);
        long routeStartedAt = System.nanoTime();
        AiRoutePlan plan = router.route(context);
        trace(
            context,
            "ROUTE",
            plan.moduleName(),
            null,
            plan.requiresClarification() ? "CLARIFICATION_REQUIRED" : "ROUTED",
            elapsedMillis(routeStartedAt),
            plan.confidence(),
            Map.of(
                "stepCount", plan.steps().size(),
                "requiresClarification", plan.requiresClarification()
            )
        );

        if (plan.requiresClarification()) {
            return AiChatResponse.clarification(plan.clarificationQuestion(), java.util.List.of());
        }

        if (plan.steps().isEmpty()) {
            return AiChatResponse.clarification("没有生成可执行步骤", java.util.List.of());
        }

        List<AiStepExecutionResult> results = new ArrayList<>();
        Object previousResult = null;

        for (AiPlanStep step : plan.steps()) {
            Optional<AiCapability> capabilityOptional = registry.find(step.moduleName(), step.intentName());
            if (capabilityOptional.isEmpty()) {
                return AiChatResponse.error("能力不存在: " + step.moduleName() + "." + step.intentName());
            }

            AiCapability capability = capabilityOptional.get();
            if (!conditionMatches(step.conditionExpression(), previousResult)) {
                String reason = "条件不满足: " + step.conditionExpression();
                results.add(AiStepExecutionResult.skipped(capability.moduleName(), capability.intentName(), reason));
                audit(context, capability, Map.of("conditionExpression", step.conditionExpression()), null, "SKIPPED", null, reason);
                trace(
                    context,
                    "STEP_CONDITION",
                    capability.moduleName(),
                    capability.intentName(),
                    "SKIPPED",
                    0L,
                    null,
                    Map.of("conditionExpression", step.conditionExpression())
                );
                continue;
            }

            Object command = argumentBinder.bind(step.arguments(), capability.commandType());

            ValidationResult validation = commandValidator.validate(command);
            if (!validation.valid()) {
                trace(
                    context,
                    "VALIDATION",
                    capability.moduleName(),
                    capability.intentName(),
                    "FAILED",
                    0L,
                    null,
                    Map.of("missingFields", validation.missingFields())
                );
                return AiChatResponse.clarification(validation.message(), validation.missingFields());
            }

            PermissionDecision permission = permissionEvaluator.check(context.user(), capability, command);
            if (!permission.allowed()) {
                audit(context, capability, command, null, "PERMISSION_DENIED", null, permission.reason());
                trace(
                    context,
                    "PERMISSION",
                    capability.moduleName(),
                    capability.intentName(),
                    "DENIED",
                    0L,
                    null,
                    Map.of("reason", permission.reason())
                );
                return AiChatResponse.permissionDenied(permission.reason());
            }

            PolicyDecision policy = policyEngine.evaluateBeforeInvoke(context, capability, command);
            if (policy.denied()) {
                audit(context, capability, command, null, "DENIED", null, policy.reason());
                trace(
                    context,
                    "POLICY",
                    capability.moduleName(),
                    capability.intentName(),
                    "DENIED",
                    0L,
                    null,
                    Map.of("reason", policy.reason())
                );
                return AiChatResponse.denied(policy.reason());
            }

            if (capability.requiresConfirmation() || policy.requiresConfirmation()) {
                AiConfirmationSnapshot snapshot = confirmationManager.create(context, capability, command, policy);
                audit(context, capability, command, snapshot.confirmationId(), "CONFIRMATION_REQUIRED", null, snapshot.reason());
                trace(
                    context,
                    "CONFIRMATION",
                    capability.moduleName(),
                    capability.intentName(),
                    "REQUIRED",
                    0L,
                    null,
                    Map.of(
                        "confirmationId", snapshot.confirmationId(),
                        "reason", snapshot.reason()
                    )
                );
                return AiChatResponse.confirmationRequired(
                    confirmationMessage(results, snapshot.reason()),
                    snapshot.confirmationId(),
                    capability.moduleName(),
                    capability.intentName(),
                    capability.riskLevel(),
                    argumentBinder.preview(command),
                    snapshot.expiresAt()
                );
            }

            long invokeStartedAt = System.nanoTime();
            AiInvokeResult invokeResult = invoker.invoke(capability, command, context);
            if (!invokeResult.success()) {
                audit(context, capability, command, null, "FAILED", null, invokeResult.errorMessage());
                trace(
                    context,
                    "INVOKE",
                    capability.moduleName(),
                    capability.intentName(),
                    "FAILED",
                    elapsedMillis(invokeStartedAt),
                    null,
                    Map.of("errorMessage", invokeResult.errorMessage())
                );
                return AiChatResponse.error(invokeResult.errorMessage());
            }

            previousResult = invokeResult.result();
            results.add(AiStepExecutionResult.success(capability.moduleName(), capability.intentName(), invokeResult.result()));
            audit(context, capability, command, null, "SUCCESS", summarize(invokeResult.result()), null);
            trace(
                context,
                "INVOKE",
                capability.moduleName(),
                capability.intentName(),
                "SUCCESS",
                elapsedMillis(invokeStartedAt),
                null,
                Map.of("resultType", invokeResult.result() == null ? "null" : invokeResult.result().getClass().getName())
            );
        }

        if (results.isEmpty()) {
            return AiChatResponse.actionResult("没有满足执行条件的步骤", plan.moduleName(), null, List.of());
        }

        AiStepExecutionResult lastResult = results.get(results.size() - 1);
        Object responseResult = results.size() == 1 ? lastResult.result() : List.copyOf(results);
        return AiChatResponse.actionResult("操作已完成", plan.moduleName(), lastResult.intentName(), responseResult);
    }

    @Override
    public AiConfirmResponse confirm(AiConfirmRequest request, AiUserContext userContext) {
        long confirmStartedAt = System.nanoTime();
        Optional<AiConfirmationSnapshot> snapshotOptional = confirmationManager.find(request.confirmationId());
        if (snapshotOptional.isEmpty()) {
            trace(
                new AiExecutionContext("unknown", "CONFIRMED_ACTION", userContext, Instant.now()),
                "CONFIRMATION_LOOKUP",
                null,
                null,
                "NOT_FOUND",
                elapsedMillis(confirmStartedAt),
                null,
                Map.of("confirmationId", request.confirmationId())
            );
            return AiConfirmResponse.notFound(request.confirmationId());
        }

        AiConfirmationSnapshot snapshot = snapshotOptional.get();
        AiExecutionContext lookupContext = new AiExecutionContext(
            snapshot.sessionId(),
            "CONFIRMED_ACTION",
            userContext,
            Instant.now()
        );
        if (!snapshot.tenantId().equals(userContext.tenantId()) || !snapshot.userId().equals(userContext.userId())) {
            trace(
                lookupContext,
                "CONFIRMATION_LOOKUP",
                snapshot.moduleName(),
                snapshot.intentName(),
                "DENIED",
                elapsedMillis(confirmStartedAt),
                null,
                Map.of("confirmationId", snapshot.confirmationId())
            );
            return AiConfirmResponse.denied(snapshot.confirmationId(), "确认记录不属于当前用户或租户");
        }
        if (snapshot.status() == ConfirmationStatus.EXECUTED) {
            trace(
                lookupContext,
                "CONFIRMATION_LOOKUP",
                snapshot.moduleName(),
                snapshot.intentName(),
                "ALREADY_EXECUTED",
                elapsedMillis(confirmStartedAt),
                null,
                Map.of("confirmationId", snapshot.confirmationId())
            );
            return AiConfirmResponse.alreadyExecuted(snapshot.confirmationId());
        }
        if (snapshot.expiresAt().isBefore(Instant.now())) {
            confirmationManager.save(snapshot.withStatus(ConfirmationStatus.EXPIRED, snapshot.confirmedAt(), snapshot.executedAt()));
            trace(
                lookupContext,
                "CONFIRMATION_LOOKUP",
                snapshot.moduleName(),
                snapshot.intentName(),
                "EXPIRED",
                elapsedMillis(confirmStartedAt),
                null,
                Map.of("confirmationId", snapshot.confirmationId())
            );
            return AiConfirmResponse.expired(snapshot.confirmationId());
        }
        if (snapshot.status() != ConfirmationStatus.PENDING) {
            trace(
                lookupContext,
                "CONFIRMATION_LOOKUP",
                snapshot.moduleName(),
                snapshot.intentName(),
                "DENIED",
                elapsedMillis(confirmStartedAt),
                null,
                Map.of(
                    "confirmationId", snapshot.confirmationId(),
                    "status", snapshot.status().name()
                )
            );
            return AiConfirmResponse.denied(snapshot.confirmationId(), "确认记录状态不可执行: " + snapshot.status());
        }

        Optional<AiCapability> capabilityOptional = registry.find(snapshot.moduleName(), snapshot.intentName());
        if (capabilityOptional.isEmpty()) {
            trace(
                lookupContext,
                "CONFIRMATION_LOOKUP",
                snapshot.moduleName(),
                snapshot.intentName(),
                "CAPABILITY_NOT_FOUND",
                elapsedMillis(confirmStartedAt),
                null,
                Map.of("confirmationId", snapshot.confirmationId())
            );
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
            trace(
                context,
                "CONFIRMATION_PERMISSION",
                capability.moduleName(),
                capability.intentName(),
                "DENIED",
                elapsedMillis(confirmStartedAt),
                null,
                Map.of("confirmationId", snapshot.confirmationId(), "reason", permission.reason())
            );
            return AiConfirmResponse.denied(snapshot.confirmationId(), permission.reason());
        }

        ValidationResult validation = commandValidator.validate(command);
        if (!validation.valid()) {
            confirmationManager.save(snapshot.withStatus(ConfirmationStatus.DENIED, Instant.now(), null));
            audit(context, capability, command, snapshot.confirmationId(), "VALIDATION_FAILED", null, validation.message());
            trace(
                context,
                "CONFIRMATION_VALIDATION",
                capability.moduleName(),
                capability.intentName(),
                "FAILED",
                elapsedMillis(confirmStartedAt),
                null,
                Map.of("confirmationId", snapshot.confirmationId(), "missingFields", validation.missingFields())
            );
            return AiConfirmResponse.denied(snapshot.confirmationId(), validation.message());
        }

        long invokeStartedAt = System.nanoTime();
        AiInvokeResult invokeResult = invoker.invoke(capability, command, context);
        Instant executedAt = Instant.now();
        confirmationManager.save(snapshot.withStatus(ConfirmationStatus.EXECUTED, executedAt, executedAt));

        if (!invokeResult.success()) {
            audit(context, capability, command, snapshot.confirmationId(), "FAILED", null, invokeResult.errorMessage());
            trace(
                context,
                "CONFIRMATION_INVOKE",
                capability.moduleName(),
                capability.intentName(),
                "FAILED",
                elapsedMillis(invokeStartedAt),
                null,
                Map.of("confirmationId", snapshot.confirmationId(), "errorMessage", invokeResult.errorMessage())
            );
            return AiConfirmResponse.denied(snapshot.confirmationId(), invokeResult.errorMessage());
        }

        audit(context, capability, command, snapshot.confirmationId(), "SUCCESS", summarize(invokeResult.result()), null);
        trace(
            context,
            "CONFIRMATION_INVOKE",
            capability.moduleName(),
            capability.intentName(),
            "SUCCESS",
            elapsedMillis(invokeStartedAt),
            null,
            Map.of("confirmationId", snapshot.confirmationId())
        );
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

    private void trace(
        AiExecutionContext context,
        String phase,
        String moduleName,
        String intentName,
        String status,
        Long latencyMs,
        Double routeConfidence,
        Map<String, ?> metadata
    ) {
        traceLogger.record(new AiTraceEvent(
            UUID.randomUUID().toString(),
            context.user().tenantId(),
            context.user().userId(),
            context.sessionId(),
            phase,
            moduleName,
            intentName,
            status,
            null,
            null,
            null,
            null,
            latencyMs,
            routeConfidence,
            toJson(metadata == null ? Map.of() : metadata),
            Instant.now()
        ));
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private String confirmationMessage(List<AiStepExecutionResult> previousResults, String reason) {
        if (previousResults.isEmpty()) {
            return reason;
        }
        return "已完成前置步骤；" + reason;
    }

    private boolean conditionMatches(String conditionExpression, Object previousResult) {
        if (conditionExpression == null || conditionExpression.isBlank()) {
            return true;
        }
        if (previousResult == null) {
            return false;
        }

        Matcher matcher = CONDITION_PATTERN.matcher(conditionExpression.trim());
        if (!matcher.matches()) {
            return false;
        }

        Object actual = readPath(previousResult, matcher.group(1));
        String operator = matcher.group(2);
        String expected = expectedValue(matcher);
        boolean equal = actual != null && expected.equals(String.valueOf(actual));
        return "==".equals(operator) ? equal : !equal;
    }

    private String expectedValue(Matcher matcher) {
        if (matcher.group(4) != null) {
            return matcher.group(4);
        }
        if (matcher.group(5) != null) {
            return matcher.group(5);
        }
        return matcher.group(3);
    }

    private Object readPath(Object source, String path) {
        Object current = source;
        for (String property : path.split("\\.")) {
            current = readProperty(current, property);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object readProperty(Object source, String property) {
        if (source instanceof Map<?, ?> map) {
            return map.get(property);
        }

        if (source.getClass().isRecord()) {
            for (RecordComponent component : source.getClass().getRecordComponents()) {
                if (component.getName().equals(property)) {
                    return invokeNoArg(source, component.getAccessor());
                }
            }
        }

        Optional<Method> method = findNoArgMethod(source.getClass(), property)
            .or(() -> findNoArgMethod(source.getClass(), "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1)));
        if (method.isPresent()) {
            return invokeNoArg(source, method.get());
        }

        try {
            Field field = source.getClass().getDeclaredField(property);
            field.setAccessible(true);
            return field.get(source);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Optional<Method> findNoArgMethod(Class<?> sourceType, String methodName) {
        try {
            Method method = sourceType.getMethod(methodName);
            if (method.getParameterCount() == 0) {
                return Optional.of(method);
            }
        } catch (NoSuchMethodException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Object invokeNoArg(Object source, Method method) {
        try {
            return method.invoke(source);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
