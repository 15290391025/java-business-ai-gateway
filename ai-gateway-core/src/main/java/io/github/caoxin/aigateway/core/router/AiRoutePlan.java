package io.github.caoxin.aigateway.core.router;

import java.util.List;

public record AiRoutePlan(
    String sessionId,
    String userInput,
    String moduleName,
    List<AiPlanStep> steps,
    double confidence,
    boolean requiresClarification,
    String clarificationQuestion
) {

    public static AiRoutePlan clarification(String sessionId, String userInput, String question) {
        return new AiRoutePlan(sessionId, userInput, null, List.of(), 0.0, true, question);
    }
}

