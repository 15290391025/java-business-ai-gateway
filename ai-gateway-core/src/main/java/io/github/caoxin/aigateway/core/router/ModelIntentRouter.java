package io.github.caoxin.aigateway.core.router;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;
import io.github.caoxin.aigateway.core.model.AiMessage;
import io.github.caoxin.aigateway.core.model.AiModelClient;
import io.github.caoxin.aigateway.core.model.AiModelRequest;
import io.github.caoxin.aigateway.core.model.AiModelResponse;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ModelIntentRouter implements AiIntentRouter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final double MIN_CONFIDENCE = 0.35;

    private final AiCapabilityRegistry registry;
    private final AiModelClient modelClient;
    private final AiIntentRouter fallbackRouter;
    private final ObjectMapper objectMapper;

    public ModelIntentRouter(
        AiCapabilityRegistry registry,
        AiModelClient modelClient,
        ObjectMapper objectMapper
    ) {
        this(registry, modelClient, new KeywordIntentRouter(registry), objectMapper);
    }

    public ModelIntentRouter(
        AiCapabilityRegistry registry,
        AiModelClient modelClient,
        AiIntentRouter fallbackRouter,
        ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.modelClient = modelClient;
        this.fallbackRouter = fallbackRouter;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiRoutePlan route(AiExecutionContext context) {
        List<AiCapability> capabilities = registry.listCapabilities(context.user());
        if (capabilities.isEmpty()) {
            return AiRoutePlan.clarification(context.sessionId(), context.userInput(), "当前用户没有可用的 AI 业务能力");
        }

        AiModelResponse response;
        try {
            response = modelClient.call(modelRequest(context, capabilities));
        } catch (RuntimeException exception) {
            return fallbackRouter.route(context);
        }

        Map<String, Object> routeResult = routeResult(response);
        if (routeResult.isEmpty()) {
            return fallbackRouter.route(context);
        }
        if (booleanValue(routeResult.get("requiresClarification")) && stepModels(routeResult).isEmpty()) {
            return AiRoutePlan.clarification(
                context.sessionId(),
                context.userInput(),
                stringValue(routeResult.get("clarificationQuestion"))
                    .orElse("我还不能确定要调用哪个业务能力，请说得更具体一些")
            );
        }

        double confidence = doubleValue(routeResult.get("confidence")).orElse(0.0);
        if (confidence < MIN_CONFIDENCE) {
            return AiRoutePlan.clarification(context.sessionId(), context.userInput(), "我还不能确定要调用哪个业务能力，请说得更具体一些");
        }

        return planFromModelResult(context, capabilities, routeResult, confidence)
            .orElseGet(() -> fallbackRouter.route(context));
    }

    private AiModelRequest modelRequest(AiExecutionContext context, List<AiCapability> capabilities) {
        return new AiModelRequest(
            List.of(
                new AiMessage("system", systemPrompt()),
                new AiMessage("user", userPrompt(context.userInput(), capabilities))
            ),
            responseSchema(),
            Map.of(),
            Map.of(
                "sessionId", context.sessionId(),
                "userId", context.user().userId(),
                "tenantId", context.user().tenantId()
            )
        );
    }

    private String systemPrompt() {
        return """
            You are a business intent router for a Java Spring Boot application.
            Only select capabilities from the allowed capability list.
            Do not invent module names, intent names, permissions, or arguments.
            You only create an execution plan; you never claim that an action has been executed.
            If the capability is unclear, set requiresClarification=true.
            If the capability is clear but required arguments are missing, return the best step with available arguments.
            The Java gateway will validate arguments and ask the user for missing fields.
            Return only a JSON object.
            """;
    }

    private String userPrompt(String userInput, List<AiCapability> capabilities) {
        return """
            User input:
            %s

            Allowed capabilities:
            %s
            """.formatted(userInput, toJson(capabilityModels(capabilities)));
    }

    private List<Map<String, Object>> capabilityModels(List<AiCapability> capabilities) {
        List<Map<String, Object>> models = new ArrayList<>();
        for (AiCapability capability : capabilities) {
            Map<String, Object> model = new LinkedHashMap<>();
            model.put("moduleName", capability.moduleName());
            model.put("moduleDescription", capability.moduleDescription());
            model.put("intentName", capability.intentName());
            model.put("intentDescription", capability.intentDescription());
            model.put("riskLevel", capability.riskLevel().name());
            model.put("requiresConfirmation", capability.requiresConfirmation());
            model.put("commandFields", commandFields(capability.commandType()));
            model.put("examples", nullSafe(capability.examples()));
            models.add(model);
        }
        return models;
    }

    private List<Map<String, Object>> commandFields(Class<?> commandType) {
        if (commandType.isRecord()) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (RecordComponent component : commandType.getRecordComponents()) {
                fields.add(commandField(component.getName(), component.getType()));
            }
            return fields;
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        for (Field field : commandType.getDeclaredFields()) {
            fields.add(commandField(field.getName(), field.getType()));
        }
        return fields;
    }

    private Map<String, Object> commandField(String name, Class<?> type) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("type", type.getSimpleName());
        return field;
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("confidence", "requiresClarification", "steps"));
        schema.put("properties", Map.of(
            "moduleName", Map.of("type", "string"),
            "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
            "requiresClarification", Map.of("type", "boolean"),
            "clarificationQuestion", Map.of("type", "string"),
            "steps", Map.of(
                "type", "array",
                "items", Map.of(
                    "type", "object",
                    "required", List.of("moduleName", "intentName", "arguments"),
                    "properties", Map.of(
                        "moduleName", Map.of("type", "string"),
                        "intentName", Map.of("type", "string"),
                        "arguments", Map.of("type", "object"),
                        "conditionExpression", Map.of("type", "string")
                    )
                )
            )
        ));
        return schema;
    }

    private Optional<AiRoutePlan> planFromModelResult(
        AiExecutionContext context,
        List<AiCapability> capabilities,
        Map<String, Object> routeResult,
        double confidence
    ) {
        Map<String, AiCapability> allowedById = capabilities.stream()
            .collect(Collectors.toMap(AiCapability::id, Function.identity()));

        List<Map<String, Object>> stepModels = stepModels(routeResult);
        if (stepModels.isEmpty()) {
            return Optional.empty();
        }

        List<AiPlanStep> steps = new ArrayList<>();
        String routeModuleName = stringValue(routeResult.get("moduleName")).orElse(null);
        String planModuleName = null;
        for (Map<String, Object> stepModel : stepModels) {
            String moduleName = stringValue(stepModel.get("moduleName")).orElse(routeModuleName);
            Optional<String> intentName = stringValue(stepModel.get("intentName"));
            if (moduleName == null || intentName.isEmpty()) {
                return Optional.empty();
            }

            AiCapability capability = allowedById.get(moduleName + "." + intentName.get());
            if (capability == null) {
                return Optional.empty();
            }

            steps.add(new AiPlanStep(
                UUID.randomUUID().toString(),
                capability.moduleName(),
                capability.intentName(),
                mapValue(stepModel.get("arguments")),
                stringValue(stepModel.get("conditionExpression")).filter(value -> !value.isBlank()).orElse(null),
                capability.riskLevel(),
                capability.requiresConfirmation()
            ));
            if (planModuleName == null) {
                planModuleName = capability.moduleName();
            }
        }

        return Optional.of(new AiRoutePlan(
            context.sessionId(),
            context.userInput(),
            planModuleName,
            List.copyOf(steps),
            confidence,
            false,
            null
        ));
    }

    private List<Map<String, Object>> stepModels(Map<String, Object> routeResult) {
        Object stepsValue = routeResult.get("steps");
        if (stepsValue instanceof List<?> list) {
            List<Map<String, Object>> steps = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> itemMap) {
                    steps.add(stringObjectMap(itemMap));
                }
            }
            return steps;
        }

        Optional<String> intentName = stringValue(routeResult.get("intentName"));
        if (intentName.isEmpty()) {
            return List.of();
        }

        Map<String, Object> step = new LinkedHashMap<>();
        stringValue(routeResult.get("moduleName")).ifPresent(value -> step.put("moduleName", value));
        step.put("intentName", intentName.get());
        step.put("arguments", mapValue(routeResult.get("arguments")));
        return List.of(step);
    }

    private Map<String, Object> routeResult(AiModelResponse response) {
        if (response == null) {
            return Map.of();
        }
        if (response.structured() != null && !response.structured().isEmpty()) {
            return response.structured();
        }
        if (response.content() == null || response.content().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(response.content(), MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringObjectMap(map);
        }
        return Map.of();
    }

    private Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Optional<String> stringValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? Optional.empty() : Optional.of(stringValue);
    }

    private Optional<Double> doubleValue(Object value) {
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        try {
            return stringValue(value).map(Double::parseDouble);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return stringValue(value)
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<String> nullSafe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
