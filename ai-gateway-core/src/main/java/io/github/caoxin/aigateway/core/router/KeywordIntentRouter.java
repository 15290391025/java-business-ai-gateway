package io.github.caoxin.aigateway.core.router;

import io.github.caoxin.aigateway.core.capability.AiCapability;
import io.github.caoxin.aigateway.core.capability.AiCapabilityRegistry;
import io.github.caoxin.aigateway.core.context.AiExecutionContext;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeywordIntentRouter implements AiIntentRouter {

    private static final Pattern ALPHA_NUMERIC_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{2,}");
    private static final Pattern DECIMAL_TOKEN = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final AiCapabilityRegistry registry;

    public KeywordIntentRouter(AiCapabilityRegistry registry) {
        this.registry = registry;
    }

    @Override
    public AiRoutePlan route(AiExecutionContext context) {
        List<AiCapability> capabilities = registry.listCapabilities(context.user());
        if (capabilities.isEmpty()) {
            return AiRoutePlan.clarification(context.sessionId(), context.userInput(), "当前用户没有可用的 AI 业务能力");
        }

        Optional<ScoredCapability> selected = capabilities.stream()
            .map(capability -> new ScoredCapability(capability, score(context.userInput(), capability)))
            .filter(scored -> scored.score() > 0)
            .max((left, right) -> Double.compare(left.score(), right.score()));

        if (selected.isEmpty() || selected.get().score() < 0.35) {
            return AiRoutePlan.clarification(context.sessionId(), context.userInput(), "我还不能确定要调用哪个业务能力，请说得更具体一些");
        }

        AiCapability capability = selected.get().capability();
        AiPlanStep step = new AiPlanStep(
            UUID.randomUUID().toString(),
            capability.moduleName(),
            capability.intentName(),
            extractArguments(context.userInput(), capability.commandType()),
            null,
            capability.riskLevel(),
            capability.requiresConfirmation()
        );

        return new AiRoutePlan(
            context.sessionId(),
            context.userInput(),
            capability.moduleName(),
            List.of(step),
            selected.get().score(),
            false,
            null
        );
    }

    private double score(String userInput, AiCapability capability) {
        String input = normalize(userInput);
        double score = 0.0;

        if (containsAny(input, "查询", "查一下", "看看", "详情", "状态") && containsAny(capability.intentName(), "query", "find", "get")) {
            score += 0.55;
        }
        if (containsAny(input, "取消", "撤销") && containsAny(capability.intentName(), "cancel", "close")) {
            score += 0.65;
        }
        if (containsAny(input, "地址", "收货") && containsAny(capability.intentName(), "address", "change", "update")) {
            score += 0.55;
        }
        if (containsAny(input, capability.moduleName(), normalize(capability.moduleDescription()))) {
            score += 0.15;
        }
        if (containsAny(input, normalize(capability.intentDescription()))) {
            score += 0.2;
        }
        for (String example : capability.examples()) {
            if (containsAny(input, normalize(example))) {
                score += 0.2;
            }
        }
        return Math.min(score, 1.0);
    }

    private Map<String, Object> extractArguments(String userInput, Class<?> commandType) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (String fieldName : commandFieldNames(commandType)) {
            if (fieldName.toLowerCase(Locale.ROOT).endsWith("id")) {
                firstMatch(ALPHA_NUMERIC_TOKEN, userInput).ifPresent(value -> arguments.put(fieldName, value));
            } else if (fieldName.toLowerCase(Locale.ROOT).contains("amount")) {
                firstMatch(DECIMAL_TOKEN, userInput).ifPresent(value -> arguments.put(fieldName, value));
            } else if (fieldName.toLowerCase(Locale.ROOT).contains("reason")) {
                arguments.put(fieldName, "用户通过自然语言操作发起");
            }
        }
        return arguments;
    }

    private List<String> commandFieldNames(Class<?> commandType) {
        if (commandType.isRecord()) {
            return List.of(commandType.getRecordComponents()).stream()
                .map(RecordComponent::getName)
                .toList();
        }
        return List.of(commandType.getDeclaredFields()).stream()
            .map(Field::getName)
            .toList();
    }

    private Optional<String> firstMatch(Pattern pattern, String source) {
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return Optional.of(matcher.group());
        }
        return Optional.empty();
    }

    private boolean containsAny(String source, String... candidates) {
        String normalizedSource = normalize(source);
        for (String candidate : candidates) {
            String normalizedCandidate = normalize(candidate);
            if (!normalizedCandidate.isBlank() && normalizedSource.contains(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private record ScoredCapability(AiCapability capability, double score) {
    }
}
