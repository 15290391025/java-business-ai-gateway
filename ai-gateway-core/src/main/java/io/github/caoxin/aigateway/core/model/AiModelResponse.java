package io.github.caoxin.aigateway.core.model;

import java.util.Map;

public record AiModelResponse(
    String content,
    Map<String, Object> structured,
    Map<String, Object> metadata
) {
}

