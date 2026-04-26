package io.github.caoxin.aigateway.core.gateway;

public record AiStepExecutionResult(
    String moduleName,
    String intentName,
    String status,
    Object result,
    String message
) {

    public static AiStepExecutionResult success(String moduleName, String intentName, Object result) {
        return new AiStepExecutionResult(moduleName, intentName, "SUCCESS", result, null);
    }

    public static AiStepExecutionResult skipped(String moduleName, String intentName, String message) {
        return new AiStepExecutionResult(moduleName, intentName, "SKIPPED", null, message);
    }
}
