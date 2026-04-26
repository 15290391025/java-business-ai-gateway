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
import io.github.caoxin.aigateway.core.session.AiSessionStateStore;
import io.github.caoxin.aigateway.core.session.InMemoryAiSessionStateStore;
import io.github.caoxin.aigateway.core.session.PendingClarification;
import io.github.caoxin.aigateway.core.trace.AiTraceEvent;
import io.github.caoxin.aigateway.core.trace.AiTraceLogger;
import io.github.caoxin.aigateway.core.validation.AiCommandValidator;
import io.github.caoxin.aigateway.core.validation.ValidationResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultAiGateway implements AiGateway {

    private static final Pattern CONDITION_PATTERN = Pattern.compile(
        "previous\\.([A-Za-z0-9_.]+)\\s*(==|!=)\\s*('([^']*)'|\"([^\"]*)\"|[A-Za-z0-9_.-]+)"
    );
    private static final Pattern ALPHA_NUMERIC_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{2,}");
    private static final Pattern DECIMAL_TOKEN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final long CLARIFICATION_EXPIRE_SECONDS = 600L;

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
    private final AiSessionStateStore sessionStateStore;

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
        this(
            registry,
            router,
            argumentBinder,
            commandValidator,
            permissionEvaluator,
            policyEngine,
            confirmationManager,
            invoker,
            auditLogger,
            traceLogger,
            objectMapper,
            new InMemoryAiSessionStateStore()
        );
    }

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
        ObjectMapper objectMapper,
        AiSessionStateStore sessionStateStore
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
        this.sessionStateStore = sessionStateStore;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request, AiUserContext userContext) {
        AiExecutionContext context = AiExecutionContext.from(request, userContext);
        Optional<PendingClarification> pendingClarification = sessionStateStore.find(context.user(), context.sessionId());
        if (pendingClarification.isPresent()) {
            return resumeClarification(context, pendingClarification.get());
        }

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

        return executePlan(context, plan, 0, new ArrayList<>(), null);
    }

    private AiChatResponse executePlan(
        AiExecutionContext context,
        AiRoutePlan plan,
        int startStepIndex,
        List<AiStepExecutionResult> results,
        Object previousResult
    ) {
        for (int stepIndex = startStepIndex; stepIndex < plan.steps().size(); stepIndex++) {
            AiPlanStep step = plan.steps().get(stepIndex);
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
                savePendingClarification(context, plan, stepIndex, step, validation, results, previousResult);
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

    private AiChatResponse resumeClarification(AiExecutionContext context, PendingClarification clarification) {
        if (clarification.stepIndex() < 0 || clarification.stepIndex() >= clarification.plan().steps().size()) {
            sessionStateStore.delete(context.user(), context.sessionId());
            return AiChatResponse.error("待补充的会话状态无效");
        }

        AiPlanStep originalStep = clarification.plan().steps().get(clarification.stepIndex());
        Optional<AiCapability> capabilityOptional = registry.find(originalStep.moduleName(), originalStep.intentName());
        if (capabilityOptional.isEmpty()) {
            sessionStateStore.delete(context.user(), context.sessionId());
            return AiChatResponse.error("能力不存在: " + originalStep.moduleName() + "." + originalStep.intentName());
        }

        AiCapability capability = capabilityOptional.get();
        Map<String, Object> mergedArguments = new LinkedHashMap<>(clarification.arguments());
        mergedArguments.putAll(extractClarificationArguments(
            context.userInput(),
            clarification.missingFields(),
            capability.commandType()
        ));

        AiPlanStep resumedStep = new AiPlanStep(
            originalStep.stepId(),
            originalStep.moduleName(),
            originalStep.intentName(),
            mergedArguments,
            originalStep.conditionExpression(),
            originalStep.riskLevel(),
            originalStep.requiresConfirmation()
        );

        List<AiPlanStep> resumedSteps = new ArrayList<>(clarification.plan().steps());
        resumedSteps.set(clarification.stepIndex(), resumedStep);
        AiRoutePlan resumedPlan = new AiRoutePlan(
            clarification.plan().sessionId(),
            clarification.originalUserInput(),
            clarification.plan().moduleName(),
            List.copyOf(resumedSteps),
            clarification.plan().confidence(),
            false,
            null
        );
        AiExecutionContext resumedContext = new AiExecutionContext(
            context.sessionId(),
            clarification.originalUserInput() + "\n补充信息: " + context.userInput(),
            context.user(),
            context.createdAt()
        );

        sessionStateStore.delete(context.user(), context.sessionId());
        trace(
            resumedContext,
            "SESSION_RESUME",
            originalStep.moduleName(),
            originalStep.intentName(),
            "RESUMED",
            0L,
            null,
            Map.of("missingFields", clarification.missingFields())
        );
        return executePlan(
            resumedContext,
            resumedPlan,
            clarification.stepIndex(),
            new ArrayList<>(clarification.priorResults()),
            clarification.previousResult()
        );
    }

    private void savePendingClarification(
        AiExecutionContext context,
        AiRoutePlan plan,
        int stepIndex,
        AiPlanStep step,
        ValidationResult validation,
        List<AiStepExecutionResult> priorResults,
        Object previousResult
    ) {
        PendingClarification clarification = new PendingClarification(
            context.user().tenantId(),
            context.user().userId(),
            context.sessionId(),
            context.userInput(),
            plan,
            stepIndex,
            step.arguments(),
            validation.missingFields(),
            priorResults,
            previousResult,
            Instant.now(),
            Instant.now().plusSeconds(CLARIFICATION_EXPIRE_SECONDS)
        );
        sessionStateStore.save(clarification);
        trace(
            context,
            "SESSION_CLARIFICATION",
            step.moduleName(),
            step.intentName(),
            "PENDING",
            0L,
            null,
            Map.of("missingFields", validation.missingFields())
        );
    }

    private Map<String, Object> extractClarificationArguments(
        String userInput,
        List<String> missingFields,
        Class<?> commandType
    ) {
        Map<String, Object> extracted = new LinkedHashMap<>();
        Map<String, Object> explicitArguments = explicitArguments(userInput);
        for (String missingField : missingFields) {
            if (explicitArguments.containsKey(missingField)) {
                extracted.put(missingField, explicitArguments.get(missingField));
                continue;
            }

            Optional<Class<?>> fieldType = commandFieldType(commandType, missingField);
            inferArgumentValue(userInput, missingField, fieldType.orElse(String.class), missingFields.size() == 1)
                .ifPresent(value -> extracted.put(missingField, value));
        }
        return extracted;
    }

    private Map<String, Object> explicitArguments(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return Map.of();
        }
        try {
            Object value = objectMapper.readValue(userInput, Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> explicitArguments = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        explicitArguments.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return explicitArguments;
            }
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
        return Map.of();
    }

    private Optional<Object> inferArgumentValue(
        String userInput,
        String fieldName,
        Class<?> fieldType,
        boolean singleMissingField
    ) {
        String normalizedFieldName = fieldName.toLowerCase(Locale.ROOT);
        Optional<String> rawValue = Optional.empty();
        if (normalizedFieldName.endsWith("id")) {
            rawValue = firstMatch(ALPHA_NUMERIC_TOKEN, userInput);
        } else if (looksNumericField(normalizedFieldName, fieldType)) {
            rawValue = firstMatch(DECIMAL_TOKEN, userInput);
        } else if (looksFreeTextField(normalizedFieldName) || singleMissingField) {
            rawValue = Optional.ofNullable(userInput).map(String::trim).filter(value -> !value.isBlank());
        }
        return rawValue.map(value -> convertFieldValue(value, fieldType));
    }

    private boolean looksNumericField(String fieldName, Class<?> fieldType) {
        return Number.class.isAssignableFrom(wrap(fieldType))
            || fieldName.contains("amount")
            || fieldName.contains("price")
            || fieldName.contains("money")
            || fieldName.contains("quantity")
            || fieldName.contains("count");
    }

    private boolean looksFreeTextField(String fieldName) {
        return fieldName.contains("reason")
            || fieldName.contains("remark")
            || fieldName.contains("comment")
            || fieldName.contains("address")
            || fieldName.contains("name");
    }

    private Object convertFieldValue(String value, Class<?> fieldType) {
        Class<?> wrappedType = wrap(fieldType);
        if (String.class.equals(wrappedType)) {
            return value;
        }
        if (Integer.class.equals(wrappedType)) {
            return Integer.valueOf(value);
        }
        if (Long.class.equals(wrappedType)) {
            return Long.valueOf(value);
        }
        if (Double.class.equals(wrappedType)) {
            return Double.valueOf(value);
        }
        if (Float.class.equals(wrappedType)) {
            return Float.valueOf(value);
        }
        if (BigDecimal.class.equals(wrappedType)) {
            return new BigDecimal(value);
        }
        return value;
    }

    private Class<?> wrap(Class<?> fieldType) {
        if (!fieldType.isPrimitive()) {
            return fieldType;
        }
        if (int.class.equals(fieldType)) {
            return Integer.class;
        }
        if (long.class.equals(fieldType)) {
            return Long.class;
        }
        if (double.class.equals(fieldType)) {
            return Double.class;
        }
        if (float.class.equals(fieldType)) {
            return Float.class;
        }
        if (boolean.class.equals(fieldType)) {
            return Boolean.class;
        }
        return fieldType;
    }

    private Optional<Class<?>> commandFieldType(Class<?> commandType, String fieldName) {
        if (commandType.isRecord()) {
            for (RecordComponent component : commandType.getRecordComponents()) {
                if (component.getName().equals(fieldName)) {
                    return Optional.of(component.getType());
                }
            }
        }

        try {
            return Optional.of(commandType.getDeclaredField(fieldName).getType());
        } catch (NoSuchFieldException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> firstMatch(Pattern pattern, String source) {
        if (source == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
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
